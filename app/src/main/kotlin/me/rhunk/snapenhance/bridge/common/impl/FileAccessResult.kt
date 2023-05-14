package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class FileAccessResult(
    var state: Boolean? = null,
    var content: ByteArray? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putBoolean("state", state!!)
        bundle.putByteArray("content", content)
    }

    override fun read(bundle: Bundle) {
        state = bundle.getBoolean("state")
        content = bundle.getByteArray("content")
    }
}
