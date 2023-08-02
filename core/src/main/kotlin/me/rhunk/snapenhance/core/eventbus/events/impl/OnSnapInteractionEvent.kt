package me.rhunk.snapenhance.core.eventbus.events.impl

import me.rhunk.snapenhance.core.eventbus.events.AbstractHookEvent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID

class OnSnapInteractionEvent(
    val conversationId: SnapUUID,
    val messageId: Long
) : AbstractHookEvent()