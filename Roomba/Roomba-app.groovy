/**
 *
 * Hubitat Import URL: https://raw.githubusercontent.com/PrayerfulDrop/Hubitat/master/Roomba/Roomba-app.groovy
 *
 *  ****************  Roomba Scheduler  ****************
 *
 *  Design Usage:
 *  This app is designed to integrate any WiFi enabled Roomba devices to have direct local connectivity and integration into Hubitat.  This applicatin will create a Roomba device based on the 
 *  the name you gave your Roomba device in the cloud app.  With this app you can schedule multiple cleaning times, automate cleaning when presence is away, receive notifications about status
 *  of the Roomba (stuck, cleaning, died, etc) and also setup continous cleaning mode for non-900 series WiFi Roomba devices.
 *
 *  Copyright 2019 Aaron Ward
 *
 *  Special thanks to Dominick Meglio for creating the initial integration and giving me permission to use his code to create this application.
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *              Donations are always appreciated: https://www.paypal.me/aaronmward
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *   1.1.1 - fixed notification options to respect user choice for what is notified
 *   1.1.0 - fixed global variables not being set
 *   1.0.9 - ability to set Roomba 900+ device settings, advanced docking options for non-900+ devices
 *   1.0.8 - determine if Roomba battery has died during docking
 *   1.0.7 - add duration for dashboard tile, minor grammar fixes
 *   1.0.6 - add all messages for dynamic dashboard tile
 *   1.0.5 - added bin full notifications, refined presence handler for additional cleaning scenarios, support for dynamic dashboard tile
 *   1.0.4 - added presence to start/dock roomba
 *   1.0.3 - changed frequency polling based on Roomba event.  Also fixed Pushover notifications to occur no matter how Roomba events are changed
 *   1.0.2 - add ability for advanced scheduling multiple times per day
 *   1.0.1 - added ability for all WiFi enabled Roomba devices to be added and controlled
 *   1.0.0 - Inital concept from Dominick Meglio
**/

def setVersion(){
    // *  V2.0.0 - 08/18/19 - Now App Watchdog compliant
	if(logEnable) log.debug "In setVersion - App Watchdog Parent app code"
    // Must match the exact name used in the json file. ie. YourFileNameParentVersion, YourFileNameChildVersion or YourFileNameDriverVersion
    state.appName = "RoombaSchedulerParentVersion"
	state.version = "1.1.1"
    if(awDevice) {
    try {
        if(sendToAWSwitch && awDevice) {
            awInfo = "${state.appName}:${state.version}"
		    awDevice.sendAWinfoMap(awInfo)
            if(logEnable) log.debug "In setVersion - Info was sent to App Watchdog"
	    }
    } catch (e) { log.error "In setVersion - ${e}" }
    }
}

definition(
    name: "Roomba Scheduler",
    namespace: "aaronward",
    author: "Aaron Ward",
    description: "Scheduling and local execution of Roomba services",
    category: "Misc",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	page name: "mainPage", title: "", install: true, uninstall: true
}

def mainPage() {
	dynamicPage(name: "mainPage") {
        section(getFormat("title", "${getImage("Blank")}" + " ${app.label}")) {
				paragraph "<div style='color:#1A77C9'>This application provides Roomba local integration and advanced scheduling.</div>"
			}

        section(getFormat("header-green", " Rest980/Dorita980 Integration")){
			input "doritaIP", "text", title: "Rest980 Server IP Address:", description: "Rest980 Server IP Address:", required: true, submitOnChange: true
			//input "doritaPort", "number", title: "Dorita Port", description: "Dorita Port", required: true, defaultValue: 3000, range: "1..65535"
		}
        section(getFormat("header-green", " Notification Device(s)")) {
		    // PushOver Devices
		     input "pushovertts", "bool", title: "Use 'Pushover' device(s)?", required: false, defaultValue: false, submitOnChange: true 
             if(pushovertts == true) {
                input "pushoverdevice", "capability.notification", title: "PushOver Device", required: true, multiple: true
                input "pushoverStart", "bool", title: "Notifications when Roomba starts cleaning?", required: false, defaultValue:false, submitOnChange: true 
                input "pushoverStop", "bool", title: "Notifications when Roomba stops cleaning?", required: false, defaultValue:false, submitOnChange: true 
                input "pushoverDock", "bool", title: "Notifications when Roomba docks and is charging?", required: false, defaultValue:false, submitOnChange: true 
                paragraph "<hr>"
                input "pushoverBin", "bool", title: "Notifications when Roomba's bin is full?", required: false, defaultValue:false, submitOnChange: true 
                input "pushoverError", "bool", title: "Notifications when Roomba has an error?", required: false, defaultValue:false, submitOnChange: true 
                input "pushoverDead", "bool", title: "Notifications when Roomba's Battery dies?", required: false, defaultValue:false, submitOnChange: true 

            }
        }
        section(getFormat("header-green", " Cleaning Schedule")) { 
            paragraph "Cleaning schedule must be set for at least <u>one day and one time</u>.<br><b>Note:</b> Roomba devices require at least 2 hours to have a full battery.  Consider this when scheduling multiple cleaning times in a day."
            input "schedDay", "enum", title: "Select which days to schedule cleaning:", required: true, multiple: true, submitOnChange: true,
                options: [
			        "1":	"Sunday",
                    "2":	"Monday",
                    "3":	"Tuesday",
                    "4":	"Wednesday",
                    "5":	"Thursday",
                    "6":	"Friday",
                    "7":	"Saturday"
                ] 
            input "timeperday", "text", title: "Number of times per day to clean:", required: true, defaultValue: "1", submitOnChange:true
            if(timeperday==null) timeperday="1"
            if(timeperday.toInteger()<1 || timeperday.toInteger()>10) { paragraph "<b>Please enter a value between 1 and 10</b><br>"
                                          } else {    for(i=0; i < timeperday.toInteger(); i++) {
                                                        switch (i) {
                                                            case 0:
                                                                proper="First"
                                                                break
                                                            case 1:
                                                                proper="Second"
                                                                break
                                                            case 2:
                                                                proper="Third"
                                                                break
                                                            case 3:
                                                                proper="Fourth"
                                                                break
                                                            case 4:
                                                                proper="Fifth"
                                                                break
                                                            case 5:
                                                                proper="Sixth"
                                                                break
                                                            case 6:
                                                                proper="Seventh"
                                                                break
                                                            case 7:
                                                                proper="Eighth"
                                                                break
                                                            case 8:
                                                                proper="Nineth"
                                                                break
                                                            case 9:
                                                                proper="Tenth"
                                                                break
                                                        }
                                                        input "day${i}", "time", title: "${proper} time:",required: true
                                                   }    
                                                }
            input "usePresence", "bool", title: "Use presence to start/stop Roomba cleaning cycle?", defaultValue: false, submitOnChange: true
            if(usePresence) {
                input "roombaPresence", "capability.presenceSensor", title: "Choose presence device(s):", multiple: true, required: true, submitOnChange: true
                 input "roombaPresenceDock", "bool", title: "Dock Roomba if someone arrives home?", defaultValue: false, submitonChange: true
            }
        }
        section(getFormat("header-green", " Advanced Options")) {
            paragraph "Roomba models below 900+ series do not have the ability to find a docking station prior to the battery dying.  Options below provide similar functionality or at least a better chance for Roomba to dock before dying."
            input "useTime", "bool", title: "Limit Roomba's cleaning time?", defaultValue: false, submitOnChange: true
            if(useTime) input "roombaLimitTime", "text", title: "How many minutes to run?", defaultValue: "60", required: false, submitOnChange: true
            input "useBattery", "bool", title: "Have Roomba dock based on battery percentage?", defaultValue: false, submitOnChange: true
            if(useBattery) input "roombaBattery", "text", title: "What percent to have Roomba begin docking?", defaultValue: "20", required: false, submitOnChange: true
            paragraph "<hr><u>Settings for Roomba 900+ series devices - See <a href=http://${doritaIP}:3000/map target=_blank>Roomba Cleaning Map</a></u>"
            input "roomba900", "bool", title: "Configure 900+ options?", defaultValue: false, submitOnChange: true
            if(roomba900){
                input "roombacarpetBoost", "enum", title: "Select Carpet Boost option:", required: false, multiple: false, defaultValue: "auto", submitOnChange: true,
                options: [
			        "auto":	"Auto",
                    "performance":	"Performance",
                    "eco":	"Eco"
                ] 
                input "roombaedgeClean", "bool", title: "Set Edge Cleaning (On/Off):", defaultValue: false, submitOnChange: true
                input "roombacleaningPasses", "enum", title: "Select Cleaning Passes option:", required: false, multiple: false, defaultValue: "auto", submitOnChange: true,
                options: [
			        "auto":	"Auto",
                    "one":	"Pass Once",
                    "two":	"Pass Twice"
                ]                 
                input "roombaalwaysFinish", "bool", title: "Set Always Finish Option (On/Off):", defaultValue: false, submitOnChange: true                
            }
        }
        section(getFormat("header-green", " Logging and Testing")) { }
            // ** App Watchdog Code **
        section("This app supports App Watchdog 2! Click here for more Information", hideable: true, hidden: true) {
				paragraph "<b>Information</b><br>See if any compatible app needs an update, all in one place!"
                paragraph "<b>Requirements</b><br> - Must install the app 'App Watchdog'. Please visit <a href='https://community.hubitat.com/t/release-app-watchdog/9952' target='_blank'>this page</a> for more information.<br> - When you are ready to go, turn on the switch below<br> - Then select 'App Watchdog Data' from the dropdown.<br> - That's it, you will now be notified automaticaly of updates."
                input(name: "sendToAWSwitch", type: "bool", defaultValue: "false", title: "Use App Watchdog to track this apps version info?", description: "Update App Watchdog", submitOnChange: "true")
			}
            if(sendToAWSwitch) {
                section(" App Watchdog 2") {    
                    if(sendToAWSwitch) input(name: "awDevice", type: "capability.actuator", title: "Please select 'App Watchdog Data' from the dropdown", submitOnChange: true, required: true, multiple: false)
			        if(sendToAWSwitch && awDevice) setVersion()
                }
            }
            // ** End App Watchdog Code **
    
        section() {
             	input "logEnable", "bool", title: "Enable Debug Logging?", required: false, defaultValue: true, submitOnChange: true
                if(logEnable) input "logMinutes", "text", title: "Log for the following number of minutes (0=logs always on):", required: false, defaultValue:15, submitOnChange: true
            //    input "init", "bool", title: "Initialize?", required: false, defaultVale:false, submitOnChange: true // For testing purposes
            if(init) {
                try {
                    initialize()
                }
                catch (any) { log.error "${any}" }
                app?.updateSetting("init",[value:"false",type:"bool"])
            }
				paragraph getFormat("line")
				paragraph "<div style='color:#1A77C9;text-align:center'>Developed by: Aaron Ward<br/>v${state.version}<br><br><a href='https://paypal.me/aaronmward?locale.x=en_US' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Donations always appreciated!</div>"
			}
	}
}

def initialize() {
    if(usePresence) subscribe(roombaPresence, "presence", presenceHandler)
	if(logEnable) log.debug "Initializing $app.label...unscheduling current jobs."
    unschedule()
    cleanupChildDevices()
	createChildDevices()
    getRoombaSchedule()
    RoombaScheduler()
    updateDevices()
    schedule("0 0 3 ? * * *", setVersion) 
    if (logEnable && logMinutes.toInteger() != 0) {
    if(logMinutes.toInteger() !=0) log.warn "Debug messages set to automatically disable in ${logMinutes} minute(s)."
        runIn((logMinutes.toInteger() * 60),logsOff)
        } else { if(logEnable && logMinutes.toInteger() == 0) {log.warn "Debug logs set to not automatically disable." } }

}

def installed() {
	if(logEnable) log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	if(logEnable) log.debug "Updated with settings: ${settings}"
    unschedule()
    setVersion()
	initialize()
}

def uninstalled() {
	if(logEnable) log.debug "Uninstalled app"

	for (device in getChildDevices())
	{
		deleteChildDevice(device.deviceNetworkId)
	}	
}

def getRoombaSchedule() {
    def roombaSchedule = []
    for(i=0; i <timeperday.toInteger(); i++) {
                switch (i) {
            case 0:
                temp = day0
                break
            case 1:
                temp = day1
                break   
            case 2:
                temp = day2
                break
            case 3:
                temp = day3
                break
            case 4:
                temp = day4
                break
            case 5:
                temp = day5
                break
            case 6:
                temp = day6
                break
            case 7:
                temp = day7
                break
            case 8:
                temp = day8
                break
            case 9:
                temp = day9
                break          
        }
    roombaSchedule.add(temp)    
    }
    state.roombaSchedule = roombaSchedule.sort()
}

def RoombaScheduler() {
    def map=[
           1:"Sunday",
           2:"Monday",
           3:"Tuesday",
           4:"Wednesday",
           5:"Thursday",
           6:"Friday",
           7:"Saturday"]
    def date = new Date()
    current = date.format("HH:mm")
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    def day = calendar.get(Calendar.DAY_OF_WEEK);
    daysofweek = schedDay.join(",")
    foundschedule=false
    cleaningday = day
    nextcleaning = state.roombaSchedule[0]
    
    // Check if we clean today
    if(daysofweek.contains(day.toString())) { 
        // Check when next scheduled cleaning time will be
            for(it in state.roombaSchedule) {
                temp = Date.parse("yyyy-MM-dd'T'HH:mm:ss", it).format('HH:mm')
                if((temp > current) && !foundschedule) {
                    nextcleaning = it
                    foundschedule=true
                }
            }
        if(!foundschedule) {
             tempday = day
             while(!foundschedule) {
                 tempday = tempday + 1
                 if(tempday>7) { tempday=1 }
                 if(daysofweek.contains(tempday.toString())) { 
                     foundschedule=true
                     cleaningday = tempday
                 }
             }
        }
      } else { 
        // Check when the next day we are cleaning
        tempday = day
         while(!foundschedule) {
             tempday = tempday + 1
             if(tempday>7) { tempday=1 }
             if(daysofweek.contains(tempday.toString())) { 
                 foundschedule=true
                 cleaningday = tempday
             }
         }
        }
    log.info "Next scheduled cleaning: ${map[cleaningday]} ${Date.parse("yyyy-MM-dd'T'HH:mm:ss", nextcleaning).format('h:mm a')}"       
    schedule("0 ${Date.parse("yyyy-MM-dd'T'HH:mm:ss", nextcleaning).format('mm H')} ? * ${cleaningday} *", RoombaSchedStart) 
}

def RoombaSchedStart() {
    def result = executeAction("/api/local/info/state")

    if (result && result.data)
    {
        def device = getChildDevice("roomba:" + result.data.name)
        if(result.data.cleanMissionStatus.phase.contains("run") || result.data.cleanMissionStatus.phase.contains("hmUsrDock") || result.data.batPct.toInteger()<75) {
            log.warn "${device} is currently cleaning.  Scheduled times may be too close together." 
        } else {
            if(result.data.cleanMissionStatus.phase.contains("charge") && result.data.batPct.toInteger()>75) {
                log.debug "Starting scheduled cleaning - Start send to ${device}"
                device.start()
            } else log.warn "${device} is currently not on the charging station.  Cannot start cleaning."
        }
    }
     RoombaScheduler()
}

// Device creation and status updhandlers
def createChildDevices() {
    def result = executeAction("/api/local/info/state")

	if (result && result.data)
    {
        if (!getChildDevice("roomba:"+result.data.name))
            addChildDevice("roomba", "Roomba", "roomba:" + result.data.name, 1234, ["name": result.data.name, isComponent: false])
    }
}

def cleanupChildDevices()
{
    def result = executeAction("/api/local/info/state")
	for (device in getChildDevices())
	{
		def deviceId = device.deviceNetworkId.replace("roomba:","")
		
        if (result.data.name != deviceId)
            deleteChildDevice(device.deviceNetworkId)
	}
}

def updateDevices() {
    // Ensure variables are set
    if(state.prevcleaning==null) state.prevcleaning = "settings"
    if(state.notified==null) state.notified = false
    if(state.batterydead==null) state.batterydead = false
    if(state.bin==null) state.bin = true
    
    def result = executeAction("/api/local/info/state")
    
    if (result && result.data)
    {
        def device = getChildDevice("roomba:" + result.data.name)
        
        device.sendEvent(name: "battery", value: result.data.batPct)
        if (!result.data.bin.present)
            device.sendEvent(name: "bin", value: "missing")
        else if (result.data.bin.full) {
            device.sendEvent(name: "bin", value: "full")
            if(pushoverBin && state.bin) {
                pushNow("${device}'s bin is full.")
                state.bin = false
            }
        } else{
            device.sendEvent(name: "bin", value: "good")
            state.bin = true
        }
        
		def status = ""
        def msg = null
		switch (result.data.cleanMissionStatus.phase){
			case "hmMidMsn":
			case "hmPostMsn":
			case "hmUsrDock":
                if(result.data.cleanMissionStatus.notReady.toInteger() == 0 && result.data.batPct == 0 && !state.batterydead) {
                    status = "dead"
                    if(logEnable) log.debug "${device}'s stopped cleaning due to battery died.  Checking status in 1 minute"
                    if(pushoverDead) {
                        msg="${device} has stopped cleaning because battery has died"
                        pushNow(msg)
                    }
                    state.batterydead = true
                    checktime = 60
                } else { 
                    if(result.data.cleanMissionStatus.notReady.toInteger() == 0 && result.data.batPct == 0) {
                        status = "dead"
                        if(logEnable) log.debug "${device}'s stopped cleaning due to battery died.  Checking status in 1 minute"
                        if(pushoverDead) msg="${device} has stopped cleaning because battery has died"
                        state.batterydead = true 
                        checktime = 60
                    } else {                    
				        status = "docking"                        
                        if(logEnable) log.debug "${device}'s docking.  Checking status in 30 seconds"
                        state.batterydead = false 
                        checktime = 30
                }
                }
				break
			case "charge":
				status = "charging"
                if(logEnable) log.debug "${device}'s charging. Checking status in 2 minutes"
                if(pushoverDock) msg="${device} has stopped cleaning and is docked and charging"
                state.batterydead = false 
                checktime = 120
				break
			case "run":
				status = "cleaning"
                if(logEnable) log.debug "${device}'s cleaning.  Checking status in 30 seconds"
                if(pushoverStart) msg="${device} has started cleaning"
                state.batterydead = false
                //control Roomba docking based on Time or Battery %
                if(useTime && roombaTime.toInteger() >= result.data.cleanMissionStatus.mssnM.toInteger()) {
                        device.stop()
                        device.dock()
                }
                if(useBattery && roombaBattery.toInteger() >= result.data.batPct.toInteger()) {
                        device.stop()
                        device.dock()
                }     
                checktime = 30
				break
			case "stop":
                if(result.data.cleanMissionStatus.notReady.toInteger() == 0) {
				    status = "idle"
                    if(logEnable) log.debug "${device}'s stopped cleaning.  Checking status in 1 minute"
                    if(pushoverStop) msg="${device} has stopped cleaning"
                } else {                
                    status = "error"
                    if(logEnable) log.debug "${device}'s stopped cleaning because it is stuck.  Checking status in 1 minute"
                    if(pushoverError) msg="${device} has stopped cleaning because of an error"
                }
                checktime = 60
				break		
		}
        runIn(checktime, updateDevices)
        state.cleaning = status
        
        device.sendEvent(name: "cleanStatus", value: status)
        if(logEnable) log.debug "Sending ${status} to ${device} dashboard tile"
        device.roombaTile(state.cleaning, result.data.batPct, result.data.cleanMissionStatus.mssnM)
        
        if(logEnable) log.trace "state.cleaning: ${state.cleaning} - state.prevcleaning: ${state.prevcleaning} - state.notified: ${state.notified}"
        
        if(!state.cleaning.contains(state.prevcleaning) && !state.notifed) {
            state.prevcleaning = state.cleaning
            if(!state.notified && msg!=null) {
                state.notified = true
                pushNow(msg)
            }
        } else {
            if(state.cleaning.contains(state.prevcleaning)) {
            state.notified = false
            }
        } 
    }
}

def pushNow(msg) {
    // If user selects Pushover notifications then send message
	if (pushovertts) {
            if (logEnable) log.debug "Sending Pushover message: ${msg}"
            pushoverdevice.deviceNotification("${msg}")
	}
}

// Handlers
def presenceHandler(evt) {
    def result = executeAction("/api/local/info/state")

    if (result && result.data)
    {
        def device = getChildDevice("roomba:" + result.data.name)
        def presence = false
        if(roombaPresence.findAll { it?.currentPresence == "present"}) { presence = true }
    
        if(presence) {
            // Presence is detected, Roomba is cleaning AND user chooses to have Roomba dock when someone is home
            if(result.data.cleanMissionStatus.phase.contains("run") && roombaPresenceDock) {
                   device.stop()
                   device.dock()
            }
        } else {
            // Presence is away start cleaning schedule and variations
            // Check if Roomba is docking, if so stop then start cleaning
            if(result.data.cleanMissionStatus.phase.contains("hmUsrDock")) {
                device.stop()
                device.start()
            }
            // Check if Roomba is charging OR is stopped then start cleaning
            if(result.data.cleanMissionStatus.phase.contains("charge") || result.data.cleanMissionStatus.phase.contains("stop")) device.start()
        }
        //update status of Roomba
        updateDevices()
    }
}

def handleStart(device, id) 
{
    if(roomba900) {
        def aresult = executeAction("/api/local/config/carpetBoost/${roombacarpetBoost}")
                
        if(roombaedgeClean) aresult = executeAction("/api/local/config/edgeClean/on")
        else aresult = executeAction("/api/local/config/edgeClean/off")
        
        aresult = executeAction("/api/local/config/cleaningPasses/${roombacleaningPasses}")
        
        if(roombaalwaysFinish) aresult = executeAction("/api/local/config/alwaysFinish/on")
        else aresult = executeAction("/api/local/config/alwaysFinish/off")
        
    }
    def result = executeAction("/api/local/action/start")
    //if (result.data.success == "null")
    //    device.sendEvent(name: "cleanStatus", value: "cleaning")
}

def handleStop(device, id) 
{
    def result = executeAction("/api/local/action/stop")
    //if (result.data.success == "null")
    //    device.sendEvent(name: "cleanStatus", value: "idle")
}

def handlePause(device, id) 
{
    def result = executeAction("/api/local/action/pause")
   // if (result.data.success == "null")
   //     device.sendEvent(name: "cleanStatus", value: "idle")
}

def handleResume(device, id) 
{
    def result = executeAction("/api/local/action/resume")
    //if (result.data.success == "null")
    //    device.sendEvent(name: "cleanStatus", value: "cleaning")
}

def handleDock(device, id) 
{
    def result = executeAction("/api/local/action/dock")
	//if (result.data.success == "null")
    //device.sendEvent(name: "cleanStatus", value: "homing")
}

def executeAction(path) {
	def params = [
        uri: "http://${doritaIP}:3000",
        path: "${path}",
		contentType: "application/json"
	]
	def result = null
	//if(logEnable) log.debug "Querying current state: ${path}"
	try
	{
		httpGet(params) { resp ->
			result = resp
		}
	}
	catch (e) 
	{
        if(path.contains("carpetBoost")) log.warn "Roomba device does not support Carpet Boost options" 
        else if(path.contains("edgeClean")) log.warn "Roomba device does not support Edge Clean options" 
        else if(path.contains("cleaningPasses")) log.warn "Roomba device does not support Cleaning Passes options" 
        else if(path.contains("alwaysFinish")) log.warn "Roomba device does not support Always Finish options" 
        else if(path.contains("state")) log.error "Rest980 Server not available: $e"
	}
	return result
}

//Application Handlers
def getImage(type) {
    def loc = "<img src='https://raw.githubusercontent.com/PrayerfulDrop/Hubitat/master/Roomba/support/roomba.png'>"
}

def getFormat(type, myText=""){
    if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#1A7BC7;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def logsOff(){
    log.warn "Debug logging disabled."
    app?.updateSetting("logEnable",[value:"false",type:"bool"])
}
