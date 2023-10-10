package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.core.wrapper.impl.Message

class BuildMessageEvent(
    val message: Message
): Event()