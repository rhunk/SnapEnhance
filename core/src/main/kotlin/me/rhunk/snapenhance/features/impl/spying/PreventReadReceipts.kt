package me.rhunk.snapenhance.features.impl.spying

import me.rhunk.snapenhance.core.eventbus.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class PreventReadReceipts : Feature("PreventReadReceipts", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        val isConversationInStealthMode: (SnapUUID) -> Boolean = hook@{
            context.feature(StealthMode::class).canUseRule(it.toString())
        }

        arrayOf("mediaMessagesDisplayed", "displayedMessages").forEach { methodName: String ->
            Hooker.hook(context.classCache.conversationManager, methodName, HookStage.BEFORE, { isConversationInStealthMode(SnapUUID(it.arg(0))) }) {
                it.setResult(null)
            }
        }

        context.event.subscribe(OnSnapInteractionEvent::class) { event ->
            if (isConversationInStealthMode(event.conversationId)) {
                event.canceled = true
            }
        }
    }
}