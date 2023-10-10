package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.core.event.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class PreventReadReceipts : Feature("PreventReadReceipts", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        val isConversationInStealthMode: (SnapUUID) -> Boolean = hook@{
            context.feature(StealthMode::class).canUseRule(it.toString())
        }

        arrayOf("mediaMessagesDisplayed", "displayedMessages").forEach { methodName: String ->
            Hooker.hook(context.classCache.conversationManager, methodName, HookStage.BEFORE, { isConversationInStealthMode(
                SnapUUID(it.arg(0))
            ) }) {
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