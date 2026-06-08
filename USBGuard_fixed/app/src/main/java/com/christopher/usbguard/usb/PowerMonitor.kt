package com.christopher.usbguard.usb

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.christopher.usbguard.model.PowerSample
import com.christopher.usbguard.model.PowerSource
import kotlin.math.abs

object PowerMonitor {

    fun read(context: Context): PowerSample {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val batteryManager = context.getSystemService(BatteryManager::class.java)

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val voltageMilliVolt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val currentMicroAmpere = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: Exception) {
            0
        }

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC_CHARGER
            BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
            0 -> PowerSource.BATTERY
            else -> PowerSource.UNKNOWN
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val voltageVolt = if (voltageMilliVolt > 0) voltageMilliVolt / 1000f else 0f

        // Android battery current
        val batteryNetCurrentAmpere = if (currentMicroAmpere != Int.MIN_VALUE && currentMicroAmpere != 0) {
            currentMicroAmpere / 1_000_000f
        } else {
            0f
        }

        val currentAmpere = abs(batteryNetCurrentAmpere)
        val batteryNetPowerWatt = voltageVolt * batteryNetCurrentAmpere
        val powerWatt = voltageVolt * currentAmpere

        val isPlugged = source != PowerSource.BATTERY && source != PowerSource.UNKNOWN

        val estimatedPowerGoingInWatt = if (isPlugged && isCharging) {
            powerWatt
        } else {
            0f
        }

        val estimatedDeviceDrawWatt = if (!isPlugged) {
            powerWatt
        } else {
            0f
        }

        val batteryPercent = if (level >= 0 && scale > 0) {
            (level.toFloat() / scale.toFloat()) * 100f
        } else {
            0f
        }

        return PowerSample(
            source = source,
            isCharging = isCharging,
            voltageVolt = voltageVolt,
            currentAmpere = currentAmpere,
            powerWatt = powerWatt,
            batteryNetCurrentAmpere = batteryNetCurrentAmpere,
            batteryNetPowerWatt = batteryNetPowerWatt,
            estimatedPowerGoingInWatt = estimatedPowerGoingInWatt,
            estimatedDeviceDrawWatt = estimatedDeviceDrawWatt,
            batteryPercent = batteryPercent,
            rawCurrentMicroAmpere = currentMicroAmpere,
            rawVoltageMilliVolt = voltageMilliVolt
        )
    }

    fun sourceLabel(source: PowerSource): String {
        return when (source) {
            PowerSource.BATTERY -> "Battery"
            PowerSource.AC_CHARGER -> "AC Charger"
            PowerSource.USB -> "USB"
            PowerSource.WIRELESS -> "Wireless"
            PowerSource.DOCK -> "Dock"
            PowerSource.UNKNOWN -> "Unknown"
        }
    }
}
