package me.rhunk.snapenhance.core.event.events

import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.hook.HookAdapter

abstract class AbstractHookEvent : Event() {
    lateinit var adapter: HookAdapter
    private val invokeLaterCallbacks = mutableListOf<() -> Unit>()

    fun addInvokeLater(callback: () -> Unit) {
        invokeLaterCallbacks.add(callback)
    }

    private fun invokeLater() {
        invokeLaterCallbacks.forEach { it() }
    }

    fun postHookEvent(block: AbstractHookEvent.() -> Unit = {}) {
        block().apply {
            invokeLater()
            if (canceled) adapter.setResult(null)
        }
    }

    fun invokeOriginal() {
        canceled = true
        invokeLater()
        adapter.invokeOriginal()
    }
}