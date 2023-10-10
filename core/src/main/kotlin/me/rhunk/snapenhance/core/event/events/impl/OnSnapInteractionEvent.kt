package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class OnSnapInteractionEvent(
    val interactionType: String,
    val conversationId: SnapUUID,
    val messageId: Long
) : AbstractHookEvent()