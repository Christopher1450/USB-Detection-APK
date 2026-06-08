package com.christopher.usbguard.model

enum class UsbMode {
    DISCONNECTED,
    CHARGING_ONLY,
    MTP,
    PTP,
    MIDI,
    RNDIS,
    ACCESSORY,
    ADB,
    UNKNOWN_DATA
}

enum class RiskLevel {
    SAFE,
    LOW,
    WARNING,
    DANGER
}

enum class PowerSource {
    BATTERY,
    AC_CHARGER,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN
}

enum class LogIntegrityState {
    EMPTY,
    VERIFIED,
    TAMPERED
}

data class LogIntegrityStatus(
    val state: LogIntegrityState = LogIntegrityState.EMPTY,
    val totalEvents: Int = 0,
    val lockedEvents: Int = 0,
    val deletableEvents: Int = 0,
    val lastVerifiedHash: String = "",
    val detail: String = "No protected log available yet"
)

data class PowerSample(
    val timestamp: Long = System.currentTimeMillis(),
    val source: PowerSource = PowerSource.UNKNOWN,
    val isCharging: Boolean = false,

    // Battery voltage reported by Android
    val voltageVolt: Float = 0f,

    // Absolute net battery current
    val currentAmpere: Float = 0f,

    // Battery power = battery voltage × battery current
    val powerWatt: Float = 0f,

    val batteryNetCurrentAmpere: Float = 0f,
    val batteryNetPowerWatt: Float = 0f,

    // Estimate charging power
    val estimatedPowerGoingInWatt: Float = 0f,

    // Estimated device draw when running from battery
    val estimatedDeviceDrawWatt: Float = 0f,

    val batteryPercent: Float = 0f,
    val rawCurrentMicroAmpere: Int = 0,
    val rawVoltageMilliVolt: Int = 0
)

data class ExternalInputInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val isExternal: Boolean
)

data class UsbAccessoryInfo(
    val manufacturer: String?,
    val model: String?,
    val description: String?,
    val version: String?,
    val uri: String?,
    val serial: String?
)

data class UsbSnapshot(
    val isMonitoring: Boolean = false,
    val monitorTick: Long = 0L,

    val powerConnected: Boolean = false,
    val usbDataConnected: Boolean = false,
    val connected: Boolean = false,
    val configured: Boolean = false,

    val mode: UsbMode = UsbMode.DISCONNECTED,
    val powerSource: PowerSource = PowerSource.UNKNOWN,
    val chargingPlugged: String = "Unknown",

    val voltageVolt: Float = 0f,
    val currentAmpere: Float = 0f,
    val powerWatt: Float = 0f,
    val batteryNetCurrentAmpere: Float = 0f,
    val batteryNetPowerWatt: Float = 0f,
    val estimatedPowerGoingInWatt: Float = 0f,
    val estimatedDeviceDrawWatt: Float = 0f,
    val batteryPercent: Float = 0f,

    val usbDeviceCount: Int = 0,
    val externalInputCount: Int = 0,

    val hostIdentityLabel: String = "Unknown",
    val usbAttachedDevices: List<UsbDescriptorInfo> = emptyList(),
    val usbAccessories: List<UsbAccessoryInfo> = emptyList(),
    val externalInputs: List<ExternalInputInfo> = emptyList(),

    val riskScore: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val message: String = "Guard stopped",
    val timestamp: Long = System.currentTimeMillis()
)

data class UsbSecurityEvent(
    val title: String,
    val detail: String,
    val riskLevel: RiskLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = "",
    val lockedUntilMillis: Long = timestamp + 3_600_000L,
    val previousHash: String = "",
    val hash: String = ""
)

data class UsbDescriptorInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val manufacturer: String?,
    val product: String?
)
