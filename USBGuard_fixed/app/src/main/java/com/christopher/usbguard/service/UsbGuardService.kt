package com.christopher.usbguard.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.christopher.usbguard.model.PowerSource
import com.christopher.usbguard.model.RiskLevel
import com.christopher.usbguard.model.UsbMode
import com.christopher.usbguard.model.UsbSecurityEvent
import com.christopher.usbguard.model.UsbSnapshot
import com.christopher.usbguard.repository.UsbGuardRepository
import com.christopher.usbguard.usb.HidInjectionDetector
import com.christopher.usbguard.usb.InputDeviceScanner
import com.christopher.usbguard.usb.PowerMonitor
import com.christopher.usbguard.usb.UsbDescriptorScanner
import com.christopher.usbguard.usb.UsbRiskEngine
import com.christopher.usbguard.usb.UsbStateMonitor

class UsbGuardService : Service() {

    private val riskEngine = UsbRiskEngine()
    private val handler = Handler(Looper.getMainLooper())

    private var monitorTick = 0L
    private var isRunning = false

    private var latestUsbSnapshot = UsbSnapshot()
    private var latestUsbDataIntent: Intent? = null

    private var hidSuspicious = false
    private var hidDetail = ""
    private var hidMonitoringArmed = false

    // Used to prevent Recent Events / protected logs from being spammed.
    // Within one physical connection session, the logger records only risk escalation
    // (for example WARNING once, then DANGER once), not every repeated scan/broadcast.
    private var maxLoggedRiskThisConnection = RiskLevel.SAFE
    private var activeRiskKey = ""

    private lateinit var hidDetector: HidInjectionDetector

    private val periodicScanner = object : Runnable {
        override fun run() {
            if (!isRunning) return

            monitorTick++
            scanAndPublish()
            handler.postDelayed(this, 1000L)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != USB_STATE_ACTION) return

            latestUsbDataIntent = Intent(intent)
            scanAndPublish(isImmediate = true)
        }
    }

    override fun onCreate() {
        super.onCreate()

        isRunning = true
        monitorTick = 0L
        activeRiskKey = ""
        maxLoggedRiskThisConnection = RiskLevel.SAFE

        UsbGuardRepository.clearPowerSamples()


        UsbGuardRepository.setEvents(emptyList())
        UsbGuardRepository.updateLogIntegrity(UsbGuardLogger.integrityStatus(this))
        NotificationHelper.createChannel(this)

        hidDetector = HidInjectionDetector(this) { detail ->
            if (!hidMonitoringArmed) return@HidInjectionDetector
            hidSuspicious = true
            hidDetail = detail
            scanAndPublish(isImmediate = true)
        }

        hidDetector.start()

        val filter = IntentFilter(USB_STATE_ACTION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        startForeground(
            NotificationHelper.FOREGROUND_ID,
            NotificationHelper.foregroundNotification(this)
        )

        UsbGuardRepository.updateSnapshot(
            UsbSnapshot(
                isMonitoring = true,
                monitorTick = 0L,
                mode = UsbMode.DISCONNECTED,
                message = "Guard started. Monitoring power, USB state, device identity, and HID activity..."
            )
        )

        handler.post(periodicScanner)
    }

    private fun scanAndPublish(isImmediate: Boolean = false) {
        if (!isRunning) return

        val previousConnected = latestUsbSnapshot.connected
        val powerSample = PowerMonitor.read(this)
        UsbGuardRepository.addPowerSample(powerSample)

        if (isTrustedPowerOnlySource(powerSample.source)) {
            latestUsbDataIntent = null
            hidSuspicious = false
            hidDetail = ""
            hidMonitoringArmed = false
        }

        val baseSnapshot = if (latestUsbDataIntent != null) {
            UsbStateMonitor.fromUsbIntent(this, latestUsbDataIntent!!, powerSample)
        } else {
            UsbStateMonitor.fromPowerOnly(powerSample)
        }

        val isNewConnection = baseSnapshot.connected && !previousConnected
        if (isNewConnection) {
            activeRiskKey = ""
            maxLoggedRiskThisConnection = RiskLevel.SAFE
        }

        if (!baseSnapshot.connected) {
            hidSuspicious = false
            hidDetail = ""
            hidMonitoringArmed = false
            activeRiskKey = ""
            maxLoggedRiskThisConnection = RiskLevel.SAFE
        }

        val descriptors = UsbDescriptorScanner.scanDevices(this)
        val accessories = UsbDescriptorScanner.scanAccessories(this)
        val externalInputs = InputDeviceScanner.scanExternalInputs()

        val hostIdentityLabel = when {
            descriptors.isNotEmpty() -> "Android is USB host / OTG mode. Attached USB device identity is available."
            accessories.isNotEmpty() -> "Android accessory mode. Accessory identity is available."
            baseSnapshot.usbDataConnected -> "Connected to USB host, but host name is not exposed to normal unrooted Android apps."
            baseSnapshot.powerConnected -> "Power source only. No USB host identity available."
            else -> "No USB or power connection."
        }

        latestUsbSnapshot = baseSnapshot.copy(
            isMonitoring = true,
            monitorTick = monitorTick,
            usbDeviceCount = descriptors.size,
            externalInputCount = externalInputs.size,
            usbAttachedDevices = descriptors,
            usbAccessories = accessories,
            externalInputs = externalInputs,
            hostIdentityLabel = hostIdentityLabel,
            timestamp = System.currentTimeMillis()
        )

        val shouldMonitorHid = shouldTrackHidFor(latestUsbSnapshot)
        if (shouldMonitorHid && (isNewConnection || isImmediate)) {
            hidDetector.notifyUsbConnected()
            hidMonitoringArmed = true
        } else if (!shouldMonitorHid) {
            hidSuspicious = false
            hidDetail = ""
            hidMonitoringArmed = false
        }

        val validSuddenHid = hidSuspicious && shouldMonitorHid

        val (evaluatedSnapshot, riskEvent) = riskEngine.evaluate(
            current = latestUsbSnapshot,
            hidAppearedSuddenly = validSuddenHid,
            descriptorCount = descriptors.size
        )

        UsbGuardRepository.updateSnapshot(
            evaluatedSnapshot.copy(
                isMonitoring = true,
                monitorTick = monitorTick
            )
        )

        if (evaluatedSnapshot.riskLevel == RiskLevel.SAFE || evaluatedSnapshot.riskLevel == RiskLevel.LOW) {
            activeRiskKey = ""
        }

        riskEvent?.let { event ->
            val finalEvent = if (validSuddenHid) {
                event.copy(detail = event.detail + " | " + hidDetail)
            } else {
                event
            }

            emitSecurityEvent(finalEvent, evaluatedSnapshot)
        }
    }

    private fun emitSecurityEvent(event: UsbSecurityEvent, snapshot: UsbSnapshot) {
        if (isTrustedPowerOnlySession(snapshot)) return

        val incomingRank = riskRank(event.riskLevel)
        val loggedRank = riskRank(maxLoggedRiskThisConnection)

        // Prevent log spam
        // in one session
        if (incomingRank <= loggedRank) return

        val key = "${event.riskLevel}:${event.title}"
        if (key == activeRiskKey && event.riskLevel == maxLoggedRiskThisConnection) return

        activeRiskKey = key
        maxLoggedRiskThisConnection = event.riskLevel

        val protectedEvent = UsbGuardLogger.log(this, event)
        UsbGuardRepository.addEvent(protectedEvent)
        UsbGuardRepository.updateLogIntegrity(UsbGuardLogger.integrityStatus(this))
        NotificationHelper.showAlert(this, protectedEvent)
    }

    private fun isTrustedPowerOnlySource(source: PowerSource): Boolean {
        return source == PowerSource.AC_CHARGER ||
            source == PowerSource.WIRELESS ||
            source == PowerSource.DOCK
    }

    private fun isTrustedPowerOnlySession(snapshot: UsbSnapshot): Boolean {
        return isTrustedPowerOnlySource(snapshot.powerSource) &&
            snapshot.powerConnected &&
            !snapshot.usbDataConnected &&
            !snapshot.configured &&
            !snapshot.mode.isDataMode() &&
            snapshot.usbAttachedDevices.isEmpty() &&
            snapshot.usbAccessories.isEmpty()
    }

    private fun shouldTrackHidFor(snapshot: UsbSnapshot): Boolean {
        return snapshot.powerSource == PowerSource.USB ||
            snapshot.usbDataConnected ||
            snapshot.configured ||
            snapshot.mode.isDataMode() ||
            snapshot.usbAttachedDevices.isNotEmpty() ||
            snapshot.usbAccessories.isNotEmpty()
    }

    private fun riskRank(level: RiskLevel): Int {
        return when (level) {
            RiskLevel.SAFE -> 0
            RiskLevel.LOW -> 1
            RiskLevel.WARNING -> 2
            RiskLevel.DANGER -> 3
        }
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

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(periodicScanner)

        runCatching { unregisterReceiver(usbReceiver) }
        runCatching { hidDetector.stop() }
        runCatching { NotificationHelper.cancelAlerts(this) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        UsbGuardRepository.updateSnapshot(
            UsbSnapshot(
                isMonitoring = false,
                monitorTick = monitorTick,
                connected = false,
                powerConnected = false,
                usbDataConnected = false,
                configured = false,
                mode = UsbMode.DISCONNECTED,
                riskScore = 0,
                riskLevel = RiskLevel.SAFE,
                message = "Guard stopped"
            )
        )

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val USB_STATE_ACTION = "android.hardware.usb.action.USB_STATE"
    }
}
