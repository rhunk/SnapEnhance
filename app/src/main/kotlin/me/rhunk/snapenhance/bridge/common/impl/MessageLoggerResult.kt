package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class MessageLoggerResult(
    var state: Boolean? = null,
    var message: ByteArray? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putBoolean("state", state!!)
        bundle.putByteArray("message", message)
    }

    override fun read(bundle: Bundle) {
        state = bundle.getBoolean("state")
        message = bundle.getByteArray("message")
    }
}