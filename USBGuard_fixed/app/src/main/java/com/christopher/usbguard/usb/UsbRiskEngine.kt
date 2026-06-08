package com.christopher.usbguard.usb

import com.christopher.usbguard.model.RiskLevel
import com.christopher.usbguard.model.UsbMode
import com.christopher.usbguard.model.UsbSecurityEvent
import com.christopher.usbguard.model.UsbSnapshot

class UsbRiskEngine(
    private val suddenWindowMs: Long = 15_000L
) {
    private var lastSnapshot: UsbSnapshot? = null

    fun evaluate(
        current: UsbSnapshot,
        hidAppearedSuddenly: Boolean,
        descriptorCount: Int
    ): Pair<UsbSnapshot, UsbSecurityEvent?> {
        val previous = lastSnapshot
        var score = 0
        val reasons = mutableListOf<String>()

        val dataModeActive = current.mode.isDataMode()
        val hasUsbDataEvidence = current.usbDataConnected || current.configured || dataModeActive
        val hasDeviceIdentityEvidence = descriptorCount > 0 || current.usbAccessories.isNotEmpty()
        val hasBadUsbEvidence = hasUsbDataEvidence || hasDeviceIdentityEvidence || hidAppearedSuddenly

        val pureWallCharging = current.powerConnected &&
            !current.usbDataConnected &&
            !current.configured &&
            !dataModeActive &&
            descriptorCount == 0 &&
            current.usbAccessories.isEmpty() &&
            !hidAppearedSuddenly

        if (!current.connected) {
            val evaluated = current.copy(
                riskScore = 0,
                riskLevel = RiskLevel.SAFE,
                message = "No power or USB connection detected"
            )
            lastSnapshot = evaluated
            return evaluated to null
        }

        if (pureWallCharging) {
            val evaluated = current.copy(
                riskScore = 0,
                riskLevel = RiskLevel.SAFE,
                message = "Safe charging-only behavior. Power source: ${current.chargingPlugged}. No USB data path, accessory, descriptor, or sudden HID evidence detected."
            )
            lastSnapshot = evaluated
            return evaluated to null
        }

        if (current.powerConnected) {
            reasons += "Power source detected: ${current.chargingPlugged}"
        }

        if (current.usbDataConnected) {
            score += 10
            reasons += "USB data-state broadcast detected"
        }

        if (current.configured) {
            score += 20
            reasons += "USB data path configured"
        }

        if (dataModeActive) {
            score += 30
            reasons += "USB mode is ${current.mode}"
        }

        if (current.mode == UsbMode.ADB) {
            score += 40
            reasons += "ADB mode indication detected"
        }

        if (descriptorCount > 0) {
            score += 20
            reasons += "USB device descriptor detected"
        }

        if (current.usbAttachedDevices.isNotEmpty()) {
            val names = current.usbAttachedDevices.joinToString { device ->
                "${device.manufacturer ?: "Unknown"} ${device.product ?: device.deviceName}"
            }
            reasons += "Attached USB device: $names"
        }

        if (current.usbAccessories.isNotEmpty()) {
            val names = current.usbAccessories.joinToString { accessory ->
                "${accessory.manufacturer ?: "Unknown"} ${accessory.model ?: "Unknown model"}"
            }
            score += 25
            reasons += "Android accessory detected: $names"
        }

        if (current.externalInputs.isNotEmpty() && hasBadUsbEvidence) {
            val names = current.externalInputs.joinToString { input -> input.name }
            score += if (hidAppearedSuddenly) 35 else 15
            reasons += "External input device detected: $names"
        }

        if (hidAppearedSuddenly) {
            score += 45
            reasons += "External keyboard/mouse appeared shortly after USB connection"
        }

        val suddenModeChange = previous != null &&
            previous.connected &&
            previous.mode == UsbMode.CHARGING_ONLY &&
            dataModeActive &&
            current.timestamp - previous.timestamp in 0..suddenWindowMs

        if (suddenModeChange) {
            score += 45
            reasons += "Sudden change from charging-only to data-transfer"
        }

        val finalScore = score.coerceIn(0, 100)

        val level = when {
            finalScore >= 75 -> RiskLevel.DANGER
            finalScore >= 45 -> RiskLevel.WARNING
            finalScore >= 15 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }

        val message = if (reasons.isEmpty()) {
            "Normal charging behavior"
        } else {
            reasons.joinToString(" | ")
        }

        val evaluated = current.copy(
            riskScore = finalScore,
            riskLevel = level,
            message = message
        )

        val event = if (level == RiskLevel.WARNING || level == RiskLevel.DANGER) {
            UsbSecurityEvent(
                title = when (level) {
                    RiskLevel.DANGER -> "High Risk USB Activity"
                    RiskLevel.WARNING -> "Suspicious USB Activity"
                    else -> "USB Activity"
                },
                detail = message,
                riskLevel = level
            )
        } else {
            null
        }

        lastSnapshot = evaluated
        return evaluated to event
    }

    private fun UsbMode.isDataMode(): Boolean {
        return this == UsbMode.MTP ||
            this == UsbMode.PTP ||
            this == UsbMode.MIDI ||
            this == UsbMode.RNDIS ||
            this == UsbMode.ACCESSORY ||
            this == UsbMode.ADB ||
            this == UsbMode.UNKNOWN_DATA
    }
}
