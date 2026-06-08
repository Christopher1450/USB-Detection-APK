package com.christopher.usbguard.usb

import android.content.Context
import android.content.Intent
import com.christopher.usbguard.model.PowerSample
import com.christopher.usbguard.model.PowerSource
import com.christopher.usbguard.model.UsbMode
import com.christopher.usbguard.model.UsbSnapshot

object UsbStateMonitor {

    fun fromUsbIntent(
        context: Context,
        intent: Intent,
        powerSample: PowerSample = PowerMonitor.read(context)
    ): UsbSnapshot {
        val usbConnected = intent.getBooleanExtra("connected", false)
        val configured = intent.getBooleanExtra("configured", false)

        val mtp = intent.getBooleanExtra("mtp", false)
        val ptp = intent.getBooleanExtra("ptp", false)
        val midi = intent.getBooleanExtra("midi", false)
        val rndis = intent.getBooleanExtra("rndis", false)
        val accessory = intent.getBooleanExtra("accessory", false)
        val adb = intent.getBooleanExtra("adb", false)

        val powerConnected = powerSample.source != PowerSource.BATTERY &&
            powerSample.source != PowerSource.UNKNOWN

        val mode = when {
            adb -> UsbMode.ADB
            mtp -> UsbMode.MTP
            ptp -> UsbMode.PTP
            midi -> UsbMode.MIDI
            rndis -> UsbMode.RNDIS
            accessory -> UsbMode.ACCESSORY
            configured -> UsbMode.UNKNOWN_DATA
            powerConnected -> UsbMode.CHARGING_ONLY
            usbConnected -> UsbMode.CHARGING_ONLY
            else -> UsbMode.DISCONNECTED
        }

        return UsbSnapshot(
            powerConnected = powerConnected,
            usbDataConnected = usbConnected,
            connected = powerConnected || usbConnected,
            configured = configured,
            mode = mode,
            powerSource = powerSample.source,
            chargingPlugged = PowerMonitor.sourceLabel(powerSample.source),
            voltageVolt = powerSample.voltageVolt,
            currentAmpere = powerSample.currentAmpere,
            powerWatt = powerSample.powerWatt,
            batteryNetCurrentAmpere = powerSample.batteryNetCurrentAmpere,
            batteryNetPowerWatt = powerSample.batteryNetPowerWatt,
            estimatedPowerGoingInWatt = powerSample.estimatedPowerGoingInWatt,
            estimatedDeviceDrawWatt = powerSample.estimatedDeviceDrawWatt,
            batteryPercent = powerSample.batteryPercent,
            timestamp = System.currentTimeMillis()
        )
    }

    fun fromPowerOnly(powerSample: PowerSample): UsbSnapshot {
        val powerConnected = powerSample.source != PowerSource.BATTERY &&
            powerSample.source != PowerSource.UNKNOWN

        val mode = if (powerConnected) {
            UsbMode.CHARGING_ONLY
        } else {
            UsbMode.DISCONNECTED
        }

        return UsbSnapshot(
            powerConnected = powerConnected,
            usbDataConnected = false,
            connected = powerConnected,
            configured = false,
            mode = mode,
            powerSource = powerSample.source,
            chargingPlugged = PowerMonitor.sourceLabel(powerSample.source),
            voltageVolt = powerSample.voltageVolt,
            currentAmpere = powerSample.currentAmpere,
            powerWatt = powerSample.powerWatt,
            batteryNetCurrentAmpere = powerSample.batteryNetCurrentAmpere,
            batteryNetPowerWatt = powerSample.batteryNetPowerWatt,
            estimatedPowerGoingInWatt = powerSample.estimatedPowerGoingInWatt,
            estimatedDeviceDrawWatt = powerSample.estimatedDeviceDrawWatt,
            batteryPercent = powerSample.batteryPercent,
            timestamp = System.currentTimeMillis()
        )
    }
}
