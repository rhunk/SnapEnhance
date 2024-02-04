package me.rhunk.snapenhance.core.scripting.impl

import me.rhunk.snapenhance.common.scripting.bindings.AbstractBinding
import me.rhunk.snapenhance.common.scripting.bindings.BindingSide
import me.rhunk.snapenhance.common.scripting.ktx.scriptableObject
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.event.Event
import me.rhunk.snapenhance.core.event.events.impl.*
import org.mozilla.javascript.ScriptableObject

class CoreEvents(
    private val modContext: ModContext
) : AbstractBinding("events", BindingSide.CORE) {
    private fun ScriptableObject.cancelableEvent(event: Event): ScriptableObject {
        defineProperty("canceled", { event.canceled }, { value -> event.canceled = value as Boolean },0)
        return this
    }

    fun onConversationUpdated(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(ConversationUpdateEvent::class) { event ->
            callback(scriptableObject("ConversationUpdateEvent") {
                putConst("conversationId", this, event.conversationId)
                putConst("conversation",this, event.conversation)
                putConst("messages", this, event.messages)
            }.cancelableEvent(event))
        }
    }

    fun onMessageBuild(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(BuildMessageEvent::class) { event ->
            callback(scriptableObject("BuildMessageEvent") {
                putConst("message", this, event.message)
            }.cancelableEvent(event))
        }
    }

    fun onViewBind(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(BindViewEvent::class) {
            callback(scriptableObject("BindViewEvent") {
                putConst("view", this, it.view)
                putConst("model", this, it.prevModel)
            }.cancelableEvent(it))
        }
    }

    fun onSnapInteraction(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(OnSnapInteractionEvent::class) { event ->
            callback(scriptableObject("OnSnapInteractionEvent") {
                putConst("interactionType", this, event.interactionType)
                putConst("conversationId", this, event.conversationId.toString())
                putConst("messageId", this, event.messageId)
            }.cancelableEvent(event))
        }
    }

    fun onPreMessageSend(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(SendMessageWithContentEvent::class) { event ->
            callback(scriptableObject("SendMessageWithContentEvent") {
                putConst("destinations", this, event.destinations)
                putConst("messageContent", this, event.messageContent)
            }.cancelableEvent(event))
        }
    }

    fun onAddView(callback: (event: ScriptableObject) -> Unit) {
        modContext.event.subscribe(AddViewEvent::class) { event ->
            callback(scriptableObject("AddViewEvent") {
                putConst("parent", this, event.parent)
                defineProperty("view", { event.view }, { value -> event.view = value as android.view.View },0)
            }.cancelableEvent(event))
        }
    }

    override fun getObject() = this
}