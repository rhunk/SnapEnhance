package me.rhunk.snapenhance.scripting.impl

import com.google.gson.JsonObject
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.impl.ConfigInterface
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.scripting.RemoteScriptManager
import java.io.File

class RemoteScriptConfig(
    private val remoteScriptManager: RemoteScriptManager,
    moduleInfo: ModuleInfo,
    private val logger: AbstractLogger,
) : ConfigInterface() {
    private val configFile = File(remoteScriptManager.getModuleDataFolder(moduleInfo.name), "config.json")
    private var config = JsonObject()

    override fun get(key: String, defaultValue: Any?): String? {
        return config[key]?.asString ?: defaultValue?.toString()
    }

    override fun set(key: String, value: Any?, save: Boolean) {
        when (value) {
            is Int -> config.addProperty(key, value)
            is Double -> config.addProperty(key, value)
            is Boolean -> config.addProperty(key, value)
            is Long -> config.addProperty(key, value)
            is Float -> config.addProperty(key, value)
            is Byte -> config.addProperty(key, value)
            is Short -> config.addProperty(key, value)
            else -> config.addProperty(key, value?.toString())
        }

        if (save) save()
    }

    override fun save() {
        configFile.writeText(config.toString())
    }

    override fun load() {
        runCatching {
            if (!configFile.exists()) {
                save()
                return@runCatching
            }
            config = remoteScriptManager.context.gson.fromJson(configFile.readText(), JsonObject::class.java)
        }.onFailure {
            logger.error("Failed to load config file", it)
            save()
        }
    }

    override fun delete() {
        configFile.delete()
    }
}