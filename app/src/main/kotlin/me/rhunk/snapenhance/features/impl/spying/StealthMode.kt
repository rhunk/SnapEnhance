package me.rhunk.snapenhance.features.impl.spying

import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.features.BridgeFileFeature
import me.rhunk.snapenhance.features.FeatureLoadParams
import android.util.Log;

class StealthMode : BridgeFileFeature("StealthMode", BridgeFileType.STEALTH, loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        readFile()
    }

    fun setStealth(conversationId: String, stealth: Boolean) {
        setState(conversationId.hashCode().toLong().toString(16), stealth)
    }

    fun isStealth(conversationId: String): Boolean {
        Log.i("Ayush", "isStealth: ${conversationId.hashCode().toLong().toString(16)}")
        return exists(conversationId.hashCode().toLong().toString(16))
    }
}
