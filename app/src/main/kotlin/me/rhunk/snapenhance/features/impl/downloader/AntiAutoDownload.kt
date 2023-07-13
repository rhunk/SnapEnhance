package me.rhunk.snapenhance.features.impl.downloader

import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.features.BridgeFileFeature
import me.rhunk.snapenhance.features.FeatureLoadParams

class AntiAutoDownload : BridgeFileFeature("AntiAutoDownload", BridgeFileType.ANTI_AUTO_DOWNLOAD, loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        readFile()
    }

    fun setUserIgnored(userId: String, state: Boolean) {
        setState(userId.hashCode().toLong().toString(16), state)
    }

    fun isUserIgnored(userId: String): Boolean {
        return exists(userId.hashCode().toLong().toString(16))
    }
}