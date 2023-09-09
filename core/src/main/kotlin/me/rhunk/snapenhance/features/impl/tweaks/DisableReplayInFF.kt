package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setEnumField
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor

class DisableReplayInFF : Feature("DisableReplayInFF", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val state by context.config.messaging.disableReplayInFF

        findClass("com.snapchat.client.messaging.InteractionInfo")
            .hookConstructor(HookStage.AFTER, { state }) { param ->
            val instance = param.thisObject<Any>()
            if (instance.getObjectField("mLongPressActionState").toString() == "REQUEST_SNAP_REPLAY") {
                instance.setEnumField("mLongPressActionState", "SHOW_CONVERSATION_ACTION_MENU")
            }
        }
    }
}