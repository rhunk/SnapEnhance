package me.rhunk.snapenhance.bridge.common.impl.messagelogger

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage


class MessageLoggerListResult(
    var messages: List<Long>? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putLongArray("messages", messages!!.map { it }.toLongArray())
    }

    override fun read(bundle: Bundle) {
        messages = bundle.getLongArray("messages")?.toList() ?: emptyList()
    }
}