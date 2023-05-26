package me.rhunk.snapenhance.bridge.common.impl.messagelogger

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class MessageLoggerRequest(
    var action: Action? = null,
    var messageId: Long? = null,
    var message: ByteArray? = null
) : BridgeMessage(){

    override fun write(bundle: Bundle) {
        bundle.putString("action", action!!.name)
        bundle.putLong("messageId", messageId!!)
        bundle.putByteArray("message", message)
    }

    override fun read(bundle: Bundle) {
        action = Action.valueOf(bundle.getString("action")!!)
        messageId = bundle.getLong("messageId")
        message = bundle.getByteArray("message")
    }

    enum class Action {
        ADD,
        GET,
        CLEAR,
        DELETE
    }
}