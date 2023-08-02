package me.rhunk.snapenhance.core.eventbus.events.impl

import me.rhunk.snapenhance.core.eventbus.events.AbstractHookEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent

class SendMessageWithContentEvent(
    val messageContent: MessageContent
) : AbstractHookEvent()