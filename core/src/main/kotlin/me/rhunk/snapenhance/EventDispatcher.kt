package me.rhunk.snapenhance

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import me.rhunk.snapenhance.core.eventbus.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.MessageDestinations
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.util.ktx.getObjectField
import me.rhunk.snapenhance.util.ktx.setObjectField
import me.rhunk.snapenhance.util.snap.SnapWidgetBroadcastReceiverHelper

class EventDispatcher(
    private val context: ModContext
) : Manager {
    override fun init() {
        context.classCache.conversationManager.hook("sendMessageWithContent", HookStage.BEFORE) { param ->
            context.event.post(SendMessageWithContentEvent(
                destinations = MessageDestinations(param.arg(0)),
                messageContent = MessageContent(param.arg(1))
            ).apply { adapter = param })?.also {
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

        ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            LayoutParams::class.java
        ).hook(HookStage.BEFORE) { param ->
            context.event.post(
                AddViewEvent(
                    parent = param.thisObject(),
                    view = param.arg(0),
                    index = param.arg(1),
                    layoutParams = param.arg(2)
                ).apply {
                    adapter = param
                }
            )?.also { event ->
                with(param) {
                    setArg(0, event.view)
                    setArg(1, event.index)
                    setArg(2, event.layoutParams)
                }
                if (event.canceled) param.setResult(null)
            }
        }

        context.classCache.networkApi.hook("submit", HookStage.BEFORE) { param ->
            val request = param.arg<Any>(0)

            context.event.post(
                NetworkApiRequestEvent(
                    url = request.getObjectField("mUrl") as String,
                    callback = param.arg(4),
                    request = request,
                ).apply {
                    adapter = param
                }
            )?.also { event ->
                event.request.setObjectField("mUrl", event.url)
                if (event.canceled) param.setResult(null)
            }
        }
    }
}