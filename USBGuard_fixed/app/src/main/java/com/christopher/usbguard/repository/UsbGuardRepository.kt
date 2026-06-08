package com.christopher.usbguard.repository

import com.christopher.usbguard.model.PowerSample
import com.christopher.usbguard.model.LogIntegrityStatus
import com.christopher.usbguard.model.UsbSecurityEvent
import com.christopher.usbguard.model.UsbSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object UsbGuardRepository {

    private val _snapshot = MutableStateFlow(UsbSnapshot())
    val snapshot: StateFlow<UsbSnapshot> = _snapshot

    private val _events = MutableStateFlow<List<UsbSecurityEvent>>(emptyList())
    val events: StateFlow<List<UsbSecurityEvent>> = _events

    private val _powerSamples = MutableStateFlow<List<PowerSample>>(emptyList())
    val powerSamples: StateFlow<List<PowerSample>> = _powerSamples

    private val _logIntegrity = MutableStateFlow(LogIntegrityStatus())
    val logIntegrity: StateFlow<LogIntegrityStatus> = _logIntegrity

    fun updateSnapshot(snapshot: UsbSnapshot) {
        _snapshot.value = snapshot
    }

    fun addEvent(event: UsbSecurityEvent) {
        _events.value = listOf(event) + _events.value.take(49)
    }

    fun setEvents(events: List<UsbSecurityEvent>) {
        _events.value = events.take(50)
    }

    fun updateLogIntegrity(status: LogIntegrityStatus) {
        _logIntegrity.value = status
    }

    fun addPowerSample(sample: PowerSample) {
        _powerSamples.value = (_powerSamples.value + sample).takeLast(60)
    }

    fun clearPowerSamples() {
        _powerSamples.value = emptyList()
    }
}
