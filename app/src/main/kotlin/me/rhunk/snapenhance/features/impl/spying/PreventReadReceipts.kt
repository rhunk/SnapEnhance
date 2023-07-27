package me.rhunk.snapenhance.features.impl.spying

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class PreventReadReceipts : Feature("PreventReadReceipts", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        val isConversationInStealthMode: (SnapUUID) -> Boolean = hook@{
            if (context.config.bool(ConfigProperty.PREVENT_READ_RECEIPTS)) return@hook true
            context.feature(StealthMode::class).isStealth(it.toString())
        }

        arrayOf("mediaMessagesDisplayed", "displayedMessages").forEach { methodName: String ->
            Hooker.hook(context.classCache.conversationManager, methodName, HookStage.BEFORE, { isConversationInStealthMode(SnapUUID(it.arg(0))) }) {
                it.setResult(null)
            }
        }
        Hooker.hook(context.classCache.snapManager, "onSnapInteraction", HookStage.BEFORE) {
            if (isConversationInStealthMode(SnapUUID(it.arg(1) as Any))) {
                it.setResult(null)
            }
        }
    }
}