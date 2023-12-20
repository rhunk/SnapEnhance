package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent

class NativeUnaryCallEvent(
    val uri: String,
    var buffer: ByteArray
) : AbstractHookEvent()