package me.rhunk.snapenhance.common.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.rhunk.snapenhance.bridge.ConfigStateListener
import me.rhunk.snapenhance.common.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.common.config.impl.RootConfig
import me.rhunk.snapenhance.common.logger.AbstractLogger
import kotlin.properties.Delegates

class ModConfig(
    private val context: Context
) {
    var locale: String = LocaleWrapper.DEFAULT_LOCALE

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FileLoaderWrapper(BridgeFileType.CONFIG, "{}".toByteArray(Charsets.UTF_8))
    var wasPresent by Delegates.notNull<Boolean>()

    /* Used to notify the bridge client about config changes */
    var configStateListener: ConfigStateListener? = null
    lateinit var root: RootConfig
        private set

    fun isInitialized() = ::root.isInitialized

    private fun createRootConfig() = RootConfig().apply { lateInit(context) }

    private fun load() {
        root = createRootConfig()
        wasPresent = file.isFileExists()
        if (!file.isFileExists()) {
            writeConfig()
            return
        }

        runCatching {
            loadConfig()
        }.onFailure {
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
        var shouldRestart = false
        var shouldCleanCache = false
        var configChanged = false

        fun compareDiff(originalContainer: ConfigContainer, modifiedContainer: ConfigContainer) {
            val parentContainerFlags = modifiedContainer.parentContainerKey?.params?.flags ?: emptySet()

            parentContainerFlags.takeIf { originalContainer.hasGlobalState }?.apply {
                if (modifiedContainer.globalState != originalContainer.globalState) {
                    configChanged = true
                    if (contains(ConfigFlag.REQUIRE_RESTART)) shouldRestart = true
                    if (contains(ConfigFlag.REQUIRE_CLEAN_CACHE)) shouldCleanCache = true
                }
            }

            for (property in modifiedContainer.properties) {
                val modifiedValue = property.value.getNullable()
                val originalValue = originalContainer.properties.entries.firstOrNull {
                    it.key.name == property.key.name
                }?.value?.getNullable()

                if (originalValue is ConfigContainer && modifiedValue is ConfigContainer) {
                    compareDiff(originalValue, modifiedValue)
                    continue
                }

                if (modifiedValue != originalValue) {
                    val flags = property.key.params.flags + parentContainerFlags
                    configChanged = true
                    if (flags.contains(ConfigFlag.REQUIRE_RESTART)) shouldRestart = true
                    if (flags.contains(ConfigFlag.REQUIRE_CLEAN_CACHE)) shouldCleanCache = true
                }
            }
        }

        val oldConfig = runCatching { file.read().toString(Charsets.UTF_8) }.getOrNull()
        file.write(exportToString().toByteArray(Charsets.UTF_8))

        configStateListener?.also {
            runCatching {
                compareDiff(createRootConfig().apply {
                    fromJson(gson.fromJson(oldConfig ?: return@runCatching, JsonObject::class.java))
                }, root)

                if (configChanged) {
                    it.onConfigChanged()
                    if (shouldCleanCache) it.onCleanCacheRequired()
                    else if (shouldRestart) it.onRestartRequired()
                }
            }.onFailure {
                AbstractLogger.directError("Error while calling config state listener", it, "ConfigStateListener")
            }
        }
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

    fun loadFromCallback(callback: (FileLoaderWrapper) -> Unit) {
        callback(file)
        load()
    }
}