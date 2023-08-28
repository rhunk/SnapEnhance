package me.rhunk.snapenhance.core.eventbus.events.impl

import me.rhunk.snapenhance.core.eventbus.events.AbstractHookEvent

class UnaryCallEvent(
    val uri: String,
    var buffer: ByteArray
) : AbstractHookEvent()