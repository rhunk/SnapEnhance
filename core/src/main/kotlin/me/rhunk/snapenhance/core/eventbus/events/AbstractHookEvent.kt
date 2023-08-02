package me.rhunk.snapenhance.core.eventbus.events

import me.rhunk.snapenhance.core.eventbus.Event
import me.rhunk.snapenhance.hook.HookAdapter

abstract class AbstractHookEvent : Event() {
    lateinit var adapter: HookAdapter
}