package com.christopher.usbguard.usb

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice

class HidInjectionDetector(
    context: Context,
    private val onSuspiciousHid: (String) -> Unit
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(InputManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var lastUsbConnectedAt: Long = -1L
    private val suspiciousWindowMs = 15_000L

    fun start() {
        inputManager.registerInputDeviceListener(this, handler)
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    fun notifyUsbConnected() {
        lastUsbConnectedAt = SystemClock.elapsedRealtime()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastUsbConnectedAt

        if (lastUsbConnectedAt <= 0 || delta !in 0..suspiciousWindowMs) return

        val sources = device.sources
        val isKeyboard = sources hasSource InputDevice.SOURCE_KEYBOARD
        val isMouse = sources hasSource InputDevice.SOURCE_MOUSE
        val isTouchpad = sources hasSource InputDevice.SOURCE_TOUCHPAD
        val isDpad = sources hasSource InputDevice.SOURCE_DPAD

        if (device.isExternal && (isKeyboard || isMouse || isTouchpad || isDpad)) {
            val detail = buildString {
                append("External input device appeared ")
                append(delta)
                append("ms after USB connect. ")
                append("name=")
                append(device.name)
                append(", vendorId=")
                append(device.vendorId)
                append(", productId=")
                append(device.productId)
                append(", sources=")
                append(device.sources)
            }

            onSuspiciousHid(detail)
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {}

    override fun onInputDeviceChanged(deviceId: Int) {}

    private infix fun Int.hasSource(source: Int): Boolean {
        return this and source == source
    }
}
