package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.MessageDestinations
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class SendMessageWithContentEvent(
    val destinations: MessageDestinations,
    val messageContent: MessageContent,
    private val callback: Any
) : AbstractHookEvent() {

    fun addCallbackResult(methodName: String, block: (args: Array<Any?>) -> Unit) {
        Hooker.ephemeralHookObjectMethod(
            callback::class.java,
            callback,
            methodName,
            HookStage.BEFORE
        ) { block(it.args()) }
    }
}