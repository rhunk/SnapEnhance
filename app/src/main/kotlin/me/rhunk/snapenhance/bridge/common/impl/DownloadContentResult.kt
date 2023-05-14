package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class DownloadContentResult(
    var state: Boolean? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putBoolean("state", state!!)
    }

    override fun read(bundle: Bundle) {
        state = bundle.getBoolean("state")
    }
}
