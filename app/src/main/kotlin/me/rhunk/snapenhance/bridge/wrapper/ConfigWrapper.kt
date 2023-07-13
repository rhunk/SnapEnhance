package me.rhunk.snapenhance.bridge.wrapper

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.config.ConfigAccessor
import me.rhunk.snapenhance.config.ConfigProperty

class ConfigWrapper: ConfigAccessor() {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
    }

    private lateinit var isFileExistsAction: () -> Boolean
    private lateinit var writeFileAction: (ByteArray) -> Unit
    private lateinit var readFileAction: () -> ByteArray

    fun load() {
        ConfigProperty.sortedByCategory().forEach { key ->
            set(key, key.valueContainer)
        }

        if (!isFileExistsAction()) {
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

    private fun loadConfig() {
        val configContent = readFileAction()

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
        writeFileAction(gson.toJson(configObject).toByteArray(Charsets.UTF_8))
    }

    fun loadFromContext(context: Context) {
        val configFile = BridgeFileType.CONFIG.resolve(context)
        isFileExistsAction = { configFile.exists() }
        readFileAction = {
            if (!configFile.exists()) {
                configFile.createNewFile()
                configFile.writeBytes("{}".toByteArray(Charsets.UTF_8))
            }
            configFile.readBytes()
        }
        writeFileAction = { configFile.writeBytes(it) }
        load()
    }

    fun loadFromBridge(bridgeClient: BridgeClient) {
        isFileExistsAction = { bridgeClient.isFileExists(BridgeFileType.CONFIG) }
        readFileAction = { bridgeClient.createAndReadFile(BridgeFileType.CONFIG, "{}".toByteArray(Charsets.UTF_8)) }
        writeFileAction = { bridgeClient.writeFile(BridgeFileType.CONFIG, it) }
        load()
    }
}