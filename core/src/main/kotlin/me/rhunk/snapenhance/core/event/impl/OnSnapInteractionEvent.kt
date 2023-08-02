package me.rhunk.snapenhance.core.event.impl

import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID

class OnSnapInteractionEvent(
    val conversationId: SnapUUID,
    val messageId: Long
) : Event()