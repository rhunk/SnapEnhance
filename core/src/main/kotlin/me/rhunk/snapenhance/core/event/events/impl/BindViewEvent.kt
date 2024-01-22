package me.rhunk.snapenhance.core.event.events.impl

import android.view.View
import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.core.util.ktx.getId

class BindViewEvent(
    val prevModel: Any,
    val nextModel: Any?,
    var view: View
): Event() {
    val chatMessageContentContainerId by lazy {
        view.resources.getId("chat_message_content_container")
    }

    inline fun chatMessage(block: (conversationId: String, messageId: String) -> Unit) {
        val modelToString = prevModel.toString()
        if (!modelToString.startsWith("ChatViewModel")) return
        if (view.id != chatMessageContentContainerId) {
            view = view.findViewById(chatMessageContentContainerId) ?: return
        }
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