package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.features.BridgeFileFeature
import me.rhunk.snapenhance.features.FeatureLoadParams

class AntiAutoSave : BridgeFileFeature("AntiAutoSave", BridgeFileType.ANTI_AUTO_SAVE, loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        readFile()
    }

    fun setConversationIgnored(userId: String, state: Boolean) {
        setState(userId.hashCode().toLong().toString(16), state)
    }

    fun isConversationIgnored(userId: String): Boolean {
        return exists(userId.hashCode().toLong().toString(16))
    }
}