package me.rhunk.snapenhance.core.eventbus.events.impl

import me.rhunk.snapenhance.core.eventbus.events.AbstractHookEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.MessageDestinations

class SendMessageWithContentEvent(
    val destinations: MessageDestinations,
    val messageContent: MessageContent
) : AbstractHookEvent()