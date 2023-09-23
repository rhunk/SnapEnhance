package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent

class NetworkApiRequestEvent(
    val request: Any,
    val callback: Any,
    var url: String,
) : AbstractHookEvent()