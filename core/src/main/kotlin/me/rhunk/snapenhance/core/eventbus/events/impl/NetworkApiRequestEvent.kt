package me.rhunk.snapenhance.core.eventbus.events.impl

import me.rhunk.snapenhance.core.eventbus.events.AbstractHookEvent

class NetworkApiRequestEvent(
    val request: Any,
    val callback: Any,
    var url: String,
) : AbstractHookEvent()