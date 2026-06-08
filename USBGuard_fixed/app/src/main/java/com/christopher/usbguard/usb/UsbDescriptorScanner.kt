package com.christopher.usbguard.usb

import android.content.Context
import android.hardware.usb.UsbManager
import com.christopher.usbguard.model.UsbAccessoryInfo
import com.christopher.usbguard.model.UsbDescriptorInfo

object UsbDescriptorScanner {

    fun scanDevices(context: Context): List<UsbDescriptorInfo> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values

        return devices.map { device ->
            UsbDescriptorInfo(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                deviceClass = device.deviceClass,
                deviceSubclass = device.deviceSubclass,
                deviceProtocol = device.deviceProtocol,
                manufacturer = try {
                    device.manufacturerName
                } catch (e: Exception) {
                    null
                },
                product = try {
                    device.productName
                } catch (e: Exception) {
                    null
                }
            )
        }
    }

    fun scanAccessories(context: Context): List<UsbAccessoryInfo> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val accessories = try {
            usbManager.accessoryList
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return accessories.map { accessory ->
            UsbAccessoryInfo(
                manufacturer = accessory.manufacturer,
                model = accessory.model,
                description = accessory.description,
                version = accessory.version,
                uri = accessory.uri,
                serial = accessory.serial
            )
        }
    }
}
