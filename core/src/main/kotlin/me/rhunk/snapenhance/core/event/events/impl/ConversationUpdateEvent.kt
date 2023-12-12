package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent
import me.rhunk.snapenhance.core.wrapper.impl.Message

class ConversationUpdateEvent(
    val conversationId: String,
    val conversation: Any?,
    val messages: List<Message>
) : AbstractHookEvent()