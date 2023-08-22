package me.rhunk.snapenhance.core.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.config.impl.RootConfig
import kotlin.properties.Delegates

class ModConfig {
    var locale: String = LocaleWrapper.DEFAULT_LOCALE

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FileLoaderWrapper(BridgeFileType.CONFIG, "{}".toByteArray(Charsets.UTF_8))
    var wasPresent by Delegates.notNull<Boolean>()

    lateinit var root: RootConfig
    operator fun getValue(thisRef: Any?, property: Any?) = root

    private fun load() {
        root = RootConfig()
        wasPresent = file.isFileExists()
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
        val configFileContent = file.read()
        val configObject = gson.fromJson(configFileContent.toString(Charsets.UTF_8), JsonObject::class.java)
        locale = configObject.get("_locale")?.asString ?: LocaleWrapper.DEFAULT_LOCALE
        root.fromJson(configObject)
    }

    fun exportToString(): String {
        val configObject = root.toJson()
        configObject.addProperty("_locale", locale)
        return configObject.toString()
    }

    fun reset() {
        root = RootConfig()
        writeConfig()
    }

    fun writeConfig() {
        file.write(exportToString().toByteArray(Charsets.UTF_8))
    }

    fun loadFromString(string: String) {
        val configObject = gson.fromJson(string, JsonObject::class.java)
        locale = configObject.get("_locale")?.asString ?: LocaleWrapper.DEFAULT_LOCALE
        root.fromJson(configObject)
        writeConfig()
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