package me.rhunk.snapenhance.core.event.impl

import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent

class SendMessageWithContentEvent(
    val messageContent: MessageContent
) : Event()