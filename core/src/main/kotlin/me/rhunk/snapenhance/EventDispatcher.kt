package me.rhunk.snapenhance

import me.rhunk.snapenhance.core.eventbus.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.manager.Manager

class EventDispatcher(
    private val context: ModContext
) : Manager {
    override fun init() {
        context.classCache.conversationManager.hook("sendMessageWithContent", HookStage.BEFORE) { param ->
            val messageContent = MessageContent(param.arg(1))
            context.event.post(SendMessageWithContentEvent(messageContent).apply { adapter = param })?.let {
                if (it.canceled) {
                    param.setResult(null)
                }
            }
        }

        context.classCache.snapManager.hook("onSnapInteraction", HookStage.BEFORE) { param ->
            val conversationId = SnapUUID(param.arg(1))
            val messageId = param.arg<Long>(2)
            context.event.post(
                OnSnapInteractionEvent(
                conversationId = conversationId,
                messageId = messageId
            )
            )?.let {
                if (it.canceled) {
                    param.setResult(null)
                }
            }
        }
    }
}