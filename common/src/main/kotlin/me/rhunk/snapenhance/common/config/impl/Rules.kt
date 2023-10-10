package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.PropertyValue
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.RuleState


class Rules : ConfigContainer() {
    private val rules = mutableMapOf<MessagingRuleType, PropertyValue<String>>()

    fun getRuleState(ruleType: MessagingRuleType): RuleState? {
        return rules[ruleType]?.getNullable()?.let { RuleState.getByName(it) }
    }

    init {
        MessagingRuleType.entries.filter { it.listMode }.forEach { ruleType ->
            rules[ruleType] = unique(ruleType.key,"whitelist", "blacklist") {
                customTranslationPath = "rules.properties.${ruleType.key}"
                customOptionTranslationPath = "rules.modes"
            }.apply {
                set("whitelist")
            }
        }
    }
}