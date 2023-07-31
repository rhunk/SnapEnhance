package me.rhunk.snapenhance.features.impl.privacy

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.NotificationType
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook

class PreventMessageSending : Feature("Prevent message sending", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val preventMessageSending by context.config.messaging.preventMessageSending

        context.classCache.conversationManager.hook("updateMessage", HookStage.BEFORE) { param ->
            val messageUpdate = param.arg<Any>(2).toString();
            if (messageUpdate == "SCREENSHOT" && preventMessageSending.contains("chat_screenshot")) {
                param.setResult(null)
            }

            if (messageUpdate == "SCREEN_RECORD" && preventMessageSending.contains("chat_screen_record")) {
                param.setResult(null)
            }
        }

        context.classCache.conversationManager.hook("sendMessageWithContent", HookStage.BEFORE) { param ->
            val message = MessageContent(param.arg(1))
            val contentType = message.contentType
            val associatedType = NotificationType.fromContentType(contentType) ?: return@hook

            if (preventMessageSending.contains(associatedType.key)) {
                Logger.debug("Preventing message sending for $associatedType")
                param.setResult(null)
            }
        }
    }
}