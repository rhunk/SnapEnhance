package me.rhunk.snapenhance.core.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.core.config.impl.RootConfig

class ModConfig() {
    val root = RootConfig()

    companion object {
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    private val file = FileLoaderWrapper(BridgeFileType.CONFIG, "{}".toByteArray(Charsets.UTF_8))

    operator fun getValue(thisRef: Any?, property: Any?) = root

    private fun load() {
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

    private fun loadConfig() {
        val configContent = file.read()
        root.fromJson(gson.fromJson(configContent.toString(Charsets.UTF_8), JsonObject::class.java))
    }

    fun writeConfig() {
        val configObject = root.toJson()
        file.write(configObject.toString().toByteArray(Charsets.UTF_8))
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