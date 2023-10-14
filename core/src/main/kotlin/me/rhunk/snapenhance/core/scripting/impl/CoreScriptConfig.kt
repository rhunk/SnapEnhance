package me.rhunk.snapenhance.core.scripting.impl

import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.scripting.impl.ConfigInterface
import me.rhunk.snapenhance.common.scripting.impl.ConfigTransactionType
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo

class CoreScriptConfig(
    private val scripting: IScripting,
    private val moduleInfo: ModuleInfo
): ConfigInterface() {
    override fun get(key: String, defaultValue: Any?): String? {
        return scripting.configTransaction(moduleInfo.name, ConfigTransactionType.GET.key, key, defaultValue.toString(), false)
    }

    override fun set(key: String, value: Any?, save: Boolean) {
        scripting.configTransaction(moduleInfo.name, ConfigTransactionType.SET.key, key, value.toString(), save)
    }

    override fun save() {
        scripting.configTransaction(moduleInfo.name, ConfigTransactionType.SAVE.key, null, null, false)
    }

    override fun load() {
        scripting.configTransaction(moduleInfo.name, ConfigTransactionType.LOAD.key, null, null, false)
    }

    override fun delete() {
        scripting.configTransaction(moduleInfo.name, ConfigTransactionType.DELETE.key, null, null, false)
    }
}