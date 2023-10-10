package me.rhunk.snapenhance.core.features

import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.RuleState

abstract class MessagingRuleFeature(name: String, val ruleType: MessagingRuleType, loadParams: Int = 0) : Feature(name, loadParams) {

    open fun getRuleState() = context.config.rules.getRuleState(ruleType)

    fun setState(conversationId: String, state: Boolean) {
        context.bridgeClient.setRule(
            context.database.getDMOtherParticipant(conversationId) ?: conversationId,
            ruleType,
            state
        )
    }

    fun getState(conversationId: String) =
        context.bridgeClient.getRules(
            context.database.getDMOtherParticipant(conversationId) ?: conversationId
        ).contains(ruleType) && getRuleState() != null

    fun canUseRule(conversationId: String): Boolean {
        val state = getState(conversationId)
        if (context.config.rules.getRuleState(ruleType) == RuleState.BLACKLIST) {
            return !state
        }
        return state
    }
}