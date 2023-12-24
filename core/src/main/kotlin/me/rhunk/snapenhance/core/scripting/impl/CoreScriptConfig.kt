package me.rhunk.snapenhance.core.scripting.impl

import me.rhunk.snapenhance.common.scripting.impl.ConfigInterface
import me.rhunk.snapenhance.common.scripting.impl.ConfigTransactionType

class CoreScriptConfig: ConfigInterface() {
    override fun get(key: String, defaultValue: Any?): String? {
        return context.runtime.scripting.configTransaction(context.moduleInfo.name, ConfigTransactionType.GET.key, key, defaultValue.toString(), false)
    }

    override fun set(key: String, value: Any?, save: Boolean) {
        context.runtime.scripting.configTransaction(context.moduleInfo.name, ConfigTransactionType.SET.key, key, value.toString(), save)
    }

    override fun save() {
        context.runtime.scripting.configTransaction(context.moduleInfo.name, ConfigTransactionType.SAVE.key, null, null, false)
    }

    override fun load() {
        context.runtime.scripting.configTransaction(context.moduleInfo.name, ConfigTransactionType.LOAD.key, null, null, false)
    }

    override fun deleteConfig() {
        context.runtime.scripting.configTransaction(context.moduleInfo.name, ConfigTransactionType.DELETE.key, null, null, false)
    }
}