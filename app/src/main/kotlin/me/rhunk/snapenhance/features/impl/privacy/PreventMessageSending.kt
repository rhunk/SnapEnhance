package me.rhunk.snapenhance.features.impl.privacy

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.NotificationType
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class PreventMessageSending : Feature("Prevent message sending", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(
            context.classCache.conversationManager,
            "sendMessageWithContent",
            HookStage.BEFORE
        ) { param ->
            val message = MessageContent(param.arg(1))
            val contentType = message.contentType
            val options = context.config.options(ConfigProperty.PREVENT_SENDING_MESSAGES)
            val associatedType = NotificationType.fromContentType(contentType) ?: return@hook

            if (options[associatedType.key] == true) {
                Logger.debug("Preventing message sending for $associatedType")
                param.setResult(null)
            }
        }
    }
}