package me.rhunk.snapenhance.manager.impl

import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.common.impl.FileAccessRequest
import me.rhunk.snapenhance.config.ConfigAccessor
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.manager.Manager
import java.nio.charset.StandardCharsets

class ConfigManager(
    private val context: ModContext,
    config: MutableMap<ConfigProperty, Any?> = mutableMapOf()
) : ConfigAccessor(config), Manager {

    private val propertyList = ConfigProperty.sortedByCategory()

    override fun init() {
        //generate default config
        propertyList.forEach { key ->
            set(key, key.defaultValue)
        }

        if (!context.bridgeClient.isFileExists(FileAccessRequest.FileType.CONFIG)) {
            writeConfig()
            return
        }

        runCatching {
            loadConfig()
        }.onFailure {
            Logger.xposedLog("Failed to load config", it)
            writeConfig()
        }
    }

    private fun loadConfig() {
        val configContent = context.bridgeClient.createAndReadFile(
            FileAccessRequest.FileType.CONFIG,
            "{}".toByteArray(Charsets.UTF_8)
        )
        val configObject: JsonObject = context.gson.fromJson(
            String(configContent, StandardCharsets.UTF_8),
            JsonObject::class.java
        )
        propertyList.forEach { key ->
            val value = context.gson.fromJson(configObject.get(key.name), key.defaultValue.javaClass) ?: key.defaultValue
            set(key, value)
        }
    }

    fun writeConfig() {
        val configObject = JsonObject()
        propertyList.forEach { key ->
            configObject.add(key.name, context.gson.toJsonTree(get(key)))
        }
        context.bridgeClient.writeFile(
            FileAccessRequest.FileType.CONFIG,
            context.gson.toJson(configObject).toByteArray(Charsets.UTF_8)
        )
    }
}