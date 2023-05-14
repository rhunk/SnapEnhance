package me.rhunk.snapenhance.features.impl.spy

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.getObjectField


class PreventStatusNotifications : Feature("PreventStatusNotifications", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(
            context.classCache.conversationManager,
            "sendMessageWithContent",
            HookStage.BEFORE,
            {context.config.bool(ConfigProperty.PREVENT_STATUS_NOTIFICATIONS) }) { param ->
            val contentTypeString = (param.arg(1) as Any).getObjectField("mContentType")

            if (contentTypeString == ContentType.STATUS_SAVE_TO_CAMERA_ROLL.name ||
                contentTypeString == ContentType.STATUS_CONVERSATION_CAPTURE_SCREENSHOT.name ||
                contentTypeString == ContentType.STATUS_CONVERSATION_CAPTURE_RECORD.name) {
                param.setResult(null)
            }
        }
    }
}
