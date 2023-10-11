package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.FeatureNotice

class Spoof : ConfigContainer() {
    inner class Device : ConfigContainer(hasGlobalState = true) {
        val fingerprint = string("fingerprint")
        val androidId = string("android_id")
        val getInstallerPackageName = string("installer_package_name")
        val debugFlag = boolean("debug_flag")
        val mockLocationState = boolean("mock_location")
        val splitClassLoader = string("split_classloader")
        val isLowEndDevice = string("low_end_device")
        val getDataDirectory = string("get_data_directory")
    }
    val device = container("device", Device()) { addNotices(FeatureNotice.BAN_RISK) }
}