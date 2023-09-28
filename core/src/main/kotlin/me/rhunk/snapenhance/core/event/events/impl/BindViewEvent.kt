package me.rhunk.snapenhance.core.event.events.impl

import android.view.View
import me.rhunk.snapenhance.core.event.Event

class BindViewEvent(
    val prevModel: Any,
    val nextModel: Any?,
    val view: View
): Event() {
    fun chatMessage(block: (conversationId: String, messageId: String) -> Unit) {
        val prevModelToString = prevModel.toString()
        if (!prevModelToString.startsWith("ChatViewModel")) return
        prevModelToString.substringAfter("messageId=").substringBefore(",").split(":").apply {
            if (size != 3) return
            block(this[0], this[2])
        }
    }

    fun friendFeedItem(block: (conversationId: String) -> Unit) {
        val prevModelToString = nextModel.toString()
        if (!prevModelToString.startsWith("FriendFeedItemViewModel")) return
        val conversationId = prevModelToString.substringAfter("conversationId: ").substringBefore("\n")
        block(conversationId)
    }
}