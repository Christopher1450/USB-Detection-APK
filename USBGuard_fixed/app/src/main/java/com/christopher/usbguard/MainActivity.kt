package com.christopher.usbguard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.christopher.usbguard.repository.UsbGuardRepository
import com.christopher.usbguard.service.UsbGuardLogger
import com.christopher.usbguard.service.UsbGuardService
import com.christopher.usbguard.ui.UsbGuardScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        UsbGuardRepository.setEvents(emptyList())
        UsbGuardRepository.updateLogIntegrity(UsbGuardLogger.integrityStatus(this))

        setContent {
            UsbGuardScreen(
                onStartGuard = {
                    val intent = Intent(this, UsbGuardService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                },
                onStopGuard = {
                    stopService(Intent(this, UsbGuardService::class.java))
                },
                onClearEligibleLogs = {
                    UsbGuardLogger.clearEligibleLogs(this)
                    UsbGuardRepository.setEvents(emptyList())
                    UsbGuardRepository.updateLogIntegrity(UsbGuardLogger.integrityStatus(this))
                }
            )
        }
    }
}
