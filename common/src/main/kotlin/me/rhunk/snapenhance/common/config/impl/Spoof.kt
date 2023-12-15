package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer

class Spoof : ConfigContainer(hasGlobalState = true) {
    val overridePlayStoreInstallerPackageName = boolean("play_store_installer_package_name")
    val fingerprint = string("fingerprint")
    val androidId = string("android_id")
    val removeVpnTransportFlag = boolean("remove_vpn_transport_flag")
    val removeMockLocationFlag = boolean("remove_mock_location_flag")
    val randomizePersistentDeviceToken = boolean("randomize_persistent_device_token")
}