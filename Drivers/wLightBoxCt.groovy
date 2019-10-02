/*
===== Blebox Hubitat Integration Driver

	Copyright 2019, Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the Hubitat Environment.

===== Hiatory =====
09.20.19	1.2.01.	Initial Parent-Child release.
					Assumption is CT is set in the first value pair (CT, Level).
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.2.01" }
metadata {
	definition (name: "bleBox wLightBox Ct",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/bleBox-Hubitat/master/Drivers/wLightBoxCt.groovy"
			   ) {
		capability "Light"
		capability "Switch"
		capability "Actuator"
		capability "Switch Level"
		capability "Color Temperature"
		capability "Refresh"
	}
	preferences {
		input ("ctLow", "number", title: "Color Temp Lower Limit", defaultValue: 2700)
		input ("ctHigh", "number", title: "Color Temp Upper Limit", defaultValue: 6500)
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	logInfo("Installing...")
	state.savedLevel = "0000"
	sendEvent(name: "colorMode", value: "CT")
	sendEvent(name: "colorTemperature", value: 2700)
	runIn(2, updated)
}

def updated() {
	logInfo("Updating...")
	unschedule()
	updateDataValue("driverVersion", driverVer())
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	state.fadeSpeed = 2
	refresh()
}


//	===== Commands and Parse Returns =====
def on() {
	logDebug("on: ${state.savedLevel}")
	parent.childCommand(getDataValue("channel"), state.savedLevel)
}

def off() {
	logDebug("off")
	parent.childCommand(getDataValue("channel"), "0000")
}

def setLevel(level, fadeSpeed = 2) {
	logDebug("setLevel: level = ${level})")
	sendEvent(name: "level", value: level.toInteger())
	state.fadeSpeed = fadeSpeed
	setColorTemperature(device.currentValue("colorTemperature"), level)
}

def setColorTemperature(ct, level = device.currentValue("level")) {
	logDebug("setColorTemperature: ${ct}K, ${level}%")
	if (ct < ctLow || ct > ctHigh) { return }
	def ctRange = ctHigh - ctLow
	def warmFactor = [510*(ctHigh-ct)/ctRange, 255].min()
	def warmLevel = (0.5 + level*warmFactor/100).toInteger()
	warmHex = hubitat.helper.HexUtils.integerToHexString(warmLevel, 1)
	def coolFactor = [510*(ct - ctLow)/ctRange, 255].min()
	def coolLevel = (0.5 + level*coolFactor/100).toInteger()
	coolHex = hubitat.helper.HexUtils.integerToHexString(coolLevel, 1)
	parent.childCommand(getDataValue("channel"), warmHex + coolHex, state.fadeSpeed)
}

def refresh() {
	logDebug("refresh.")
	parent.refresh()
}

def parseReturnData(hexDesired) {
	logDebug("parseReturnData: ${hexDesired}")
	def hexLevel
	switch(getDataValue("channel")) {
		case "ct1":
			hexLevel = hexDesired[0..3]
			break
		case "ct2":
			hexLevel = hexDesired[4..7]
			break
		default: return
	}
	if (hexLevel == "0000") {
		sendEvent(name: "switch", value: "off")
		logInfo("parseReturnData: Device id Off")
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedLevel = hexLevel
		def ct = device.currentValue("colorTemperature")
		def level = device.currentValue("level")
		def coolFactor = 100 * hubitat.helper.HexUtils.hexStringToInt(hexLevel[0..1]) / level
		def warmFactor = 100 * hubitat.helper.HexUtils.hexStringToInt(hexLevel[2..3]) / level
		def ctRange = ctHigh - ctLow
		if (coolFactor <= warmFactor) {
		ct = ctHigh - (0.5 + coolFactor * ctRange/510).toInteger()
		} else {
			ct = ctLow + (0.5 + warmFactor * ctRange/510).toInteger()
		}
		sendEvent(name: "colorTemperature", value: ct)
		logInfo("parseReturnData: On, color temp: ${ct}K, level: ${level}%")
		setColorTempData(ct)
	}
}

def setColorTempData(temp){
	logDebug("setColorTempData: color temperature = ${temp}")
    def value = temp.toInteger()
	state.lastColorTemp = value
    def genericName
	if (value <= 2800) { genericName = "Incandescent" }
	else if (value <= 3300) { genericName = "Soft White" }
	else if (value <= 3500) { genericName = "Warm White" }
	else if (value <= 4150) { genericName = "Moonlight" }
	else if (value <= 5000) { genericName = "Horizon" }
	else if (value <= 5500) { genericName = "Daylight" }
	else if (value <= 6000) { genericName = "Electronic" }
	else if (value <= 6500) { genericName = "Skylight" }
	else { genericName = "Polar" }
	sendEvent(name: "colorName", value: genericName)
	logInfo("setColorTempData: color name is ${genericName}.")
}


//	===== Utility Methods =====
def logInfo(msg) {
	if (descriptionText == true) { log.info "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logWarn(msg){ log.warn "<b>${device.label} ${driverVer()}</b> ${msg}" }

//	end-of-file