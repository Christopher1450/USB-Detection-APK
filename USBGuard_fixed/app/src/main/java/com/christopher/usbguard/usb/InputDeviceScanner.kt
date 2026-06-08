package com.christopher.usbguard.usb

import android.view.InputDevice
import com.christopher.usbguard.model.ExternalInputInfo

object InputDeviceScanner {

    fun scanExternalInputs(): List<ExternalInputInfo> {
        val result = mutableListOf<ExternalInputInfo>()
        val ids: IntArray = InputDevice.getDeviceIds()

        for (id in ids) {
            val device: InputDevice = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isExternal = device.isExternal

            if (!isExternal) continue
            if (!isInputSource(sources)) continue

            result.add(
                ExternalInputInfo(
                    name = device.name ?: "Unknown input device",
                    vendorId = device.vendorId,
                    productId = device.productId,
                    sources = sources,
                    isExternal = isExternal
                )
            )
        }

        return result
    }

    private fun isInputSource(sources: Int): Boolean {
        return sources hasSource InputDevice.SOURCE_KEYBOARD ||
            sources hasSource InputDevice.SOURCE_MOUSE ||
            sources hasSource InputDevice.SOURCE_TOUCHPAD ||
            sources hasSource InputDevice.SOURCE_DPAD
    }

    private infix fun Int.hasSource(source: Int): Boolean {
        return this and source == source
    }
}
