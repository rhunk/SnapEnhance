package me.rhunk.snapenhance

import android.content.Intent
import me.rhunk.snapenhance.core.eventbus.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.util.snap.SnapWidgetBroadcastReceiverHelper

class EventDispatcher(
    private val context: ModContext
) : Manager {
    override fun init() {
        context.classCache.conversationManager.hook("sendMessageWithContent", HookStage.BEFORE) { param ->
            val messageContent = MessageContent(param.arg(1))
            context.event.post(SendMessageWithContentEvent(messageContent).apply { adapter = param })?.also {
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
            )?.also {
                if (it.canceled) {
                    param.setResult(null)
                }
            }
        }

        context.androidContext.classLoader.loadClass(SnapWidgetBroadcastReceiverHelper.CLASS_NAME)
            .hook("onReceive", HookStage.BEFORE) { param ->
            val intent = param.arg(1) as? Intent ?: return@hook
            if (!SnapWidgetBroadcastReceiverHelper.isIncomingIntentValid(intent)) return@hook
            val action = intent.getStringExtra("action") ?: return@hook

            context.event.post(
                SnapWidgetBroadcastReceiveEvent(
                    androidContext = context.androidContext,
                    intent = intent,
                    action = action
                )
            )?.also {
                if (it.canceled) {
                    param.setResult(null)
                }
            }
        }
    }
}