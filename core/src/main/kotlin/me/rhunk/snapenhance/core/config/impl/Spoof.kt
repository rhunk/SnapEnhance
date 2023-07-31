package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class Spoof : ConfigContainer() {
    inner class Location : ConfigContainer(globalState = false) {
        val latitude = float("location_latitude")
        val longitude = float("location_longitude")
    }
    val location = container("location", Location())

    inner class Device : ConfigContainer(globalState = false) {
        val fingerprint = string("device_fingerprint")
        val androidId = string("device_android_id")
    }
    val device = container("device", Device())
}