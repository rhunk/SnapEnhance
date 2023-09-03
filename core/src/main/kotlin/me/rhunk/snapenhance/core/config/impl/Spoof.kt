package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice

class Spoof : ConfigContainer() {
    inner class Location : ConfigContainer(hasGlobalState = true) {
        val latitude = float("location_latitude")
        val longitude = float("location_longitude")
    }
    val location = container("location", Location())

    inner class Device : ConfigContainer(hasGlobalState = true) {
        val fingerprint = string("fingerprint")
        val androidId = string("android_id")
        val getInstallerPackageName = string("installer_package_name")
        val debugFlag = boolean("debug_flag")
        val mockLocationState = boolean("mock_location")
        val splitClassLoader = string("split_classloader")
    }
    val device = container("device", Device()) { addNotices(FeatureNotice.BAN_RISK) }
}