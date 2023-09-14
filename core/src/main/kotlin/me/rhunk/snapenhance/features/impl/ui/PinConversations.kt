package me.rhunk.snapenhance.features.impl.ui

import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.messaging.RuleState
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.hook.hookConstructor

class PinConversations : MessagingRuleFeature("PinConversations", MessagingRuleType.PIN_CONVERSATION, loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        context.classCache.feedManager.hook("setPinnedConversationStatus", HookStage.BEFORE) { param ->
            val conversationUUID = SnapUUID(param.arg(0))
            val isPinned = param.arg<Any>(1).toString() == "PINNED"
            setState(conversationUUID.toString(), isPinned)
        }

        context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationUUID = SnapUUID(instance.getObjectField("mConversationId"))
            if (getState(conversationUUID.toString())) {
                instance.setObjectField("mPinnedTimestampMs", 1L)
            }
        }

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationUUID = SnapUUID(instance.getObjectField("mConversationId") ?: return@hookConstructor)
            val isPinned = getState(conversationUUID.toString())
            if (isPinned) {
                instance.setObjectField("mPinnedTimestampMs", 1L)
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}