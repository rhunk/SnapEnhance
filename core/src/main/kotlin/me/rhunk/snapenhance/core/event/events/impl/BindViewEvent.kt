package me.rhunk.snapenhance.core.event.events.impl

import android.view.View
import me.rhunk.snapenhance.core.event.Event

class BindViewEvent(
    val prevModel: Any,
    val nextModel: Any?,
    val view: View
): Event() {
    inline fun chatMessage(block: (conversationId: String, messageId: String) -> Unit) {
        val modelToString = prevModel.toString()
        if (!modelToString.startsWith("ChatViewModel")) return
        modelToString.substringAfter("messageId=").substringBefore(",").split(":").apply {
            if (size != 3) return
            block(this[0], this[2])
        }
    }

    inline fun friendFeedItem(block: (conversationId: String) -> Unit) {
        val modelToString = prevModel.toString()
        if (!modelToString.startsWith("FriendFeedItemViewModel")) return
        val conversationId = modelToString.substringAfter("conversationId: ").substringBefore("\n")
        block(conversationId)
    }
}