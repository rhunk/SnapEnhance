package me.rhunk.snapenhance.features.impl.privacy

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class PreventMessageSending : Feature("Send message override", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(
            context.classCache.conversationManager,
            "sendMessageWithContent",
            HookStage.BEFORE
        ) { param ->
            val message = MessageContent(param.arg(1))
            val contentType = message.contentType

            if (context.config.bool(ConfigProperty.PREVENT_STATUS_NOTIFICATIONS)) {
                if (contentType == ContentType.STATUS_SAVE_TO_CAMERA_ROLL ||
                        contentType == ContentType.STATUS_CALL_MISSED_AUDIO ||
                        contentType == ContentType.STATUS_CALL_MISSED_VIDEO) {
                    param.setResult(null)
                }
            }

            if (context.config.bool(ConfigProperty.PREVENT_SCREENSHOT_NOTIFICATIONS)) {
                if (contentType == ContentType.STATUS_CONVERSATION_CAPTURE_SCREENSHOT ||
                    contentType == ContentType.STATUS_CONVERSATION_CAPTURE_RECORD) {
                    param.setResult(null)
                }
            }
        }
    }
}