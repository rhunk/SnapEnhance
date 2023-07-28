package me.rhunk.snapenhance.bridge.wrapper

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.config.ConfigAccessor
import me.rhunk.snapenhance.config.ConfigProperty

class ConfigWrapper: ConfigAccessor() {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
    }

    private val file = FileLoaderWrapper(BridgeFileType.CONFIG, "{}".toByteArray(Charsets.UTF_8))

    fun load() {
        ConfigProperty.sortedByCategory().forEach { key ->
            set(key, key.valueContainer)
        }

        if (!file.isFileExists()) {
            writeConfig()
            return
        }

        runCatching {
            loadConfig()
        }.onFailure {
            Logger.error("Failed to load config", it)
            writeConfig()
        }
    }

    fun save() {
        writeConfig()
    }

    private fun loadConfig() {
        val configContent = file.read()

        val configObject: JsonObject = gson.fromJson(
            configContent.toString(Charsets.UTF_8),
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

        file.write(gson.toJson(configObject).toByteArray(Charsets.UTF_8))
    }

    fun loadFromContext(context: Context) {
        file.loadFromContext(context)
        load()
    }

    fun loadFromBridge(bridgeClient: BridgeClient) {
        file.loadFromBridge(bridgeClient)
        load()
    }
}