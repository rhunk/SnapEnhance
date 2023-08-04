package me.rhunk.snapenhance.manager.impl

import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.config.ConfigAccessor
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.manager.Manager
import java.nio.charset.StandardCharsets

class ConfigManager(
    private val context: ModContext
) : ConfigAccessor(), Manager {

    override fun init() {
        ConfigProperty.sortedByCategory().forEach { key ->
            set(key, key.valueContainer)
        }

        if (!context.bridgeClient.isFileExists(BridgeFileType.CONFIG)) {
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
            BridgeFileType.CONFIG,
            "{}".toByteArray(Charsets.UTF_8)
        )
        val configObject: JsonObject = context.gson.fromJson(
            String(configContent, StandardCharsets.UTF_8),
            JsonObject::class.java
        )
        entries().forEach { (key, value) ->
            value.writeFrom(configObject.get(key.name)?.asString ?: value.read())
        }
    }

    fun writeConfig() {
        val configObject = JsonObject()
        entries().forEach { (key, value) ->
            configObject.addProperty(key.name, value.read())
        }
        context.bridgeClient.writeFile(
            BridgeFileType.CONFIG,
            context.gson.toJson(configObject).toByteArray(Charsets.UTF_8)
        )
    }
}