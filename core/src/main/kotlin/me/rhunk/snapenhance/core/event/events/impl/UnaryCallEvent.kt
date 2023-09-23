package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent

class UnaryCallEvent(
    val uri: String,
    var buffer: ByteArray
) : AbstractHookEvent()