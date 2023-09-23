package me.rhunk.snapenhance.core.event.events

import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.hook.HookAdapter

abstract class AbstractHookEvent : Event() {
    lateinit var adapter: HookAdapter
}