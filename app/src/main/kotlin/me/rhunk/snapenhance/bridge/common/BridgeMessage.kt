package me.rhunk.snapenhance.bridge.common

import android.os.Bundle
import android.os.Message

abstract class BridgeMessage {
    abstract fun write(bundle: Bundle)
    abstract fun read(bundle: Bundle)

    fun toMessage(what: Int): Message {
        val message = Message.obtain(null, what)
        message.data = Bundle()
        write(message.data)
        return message
    }
}
