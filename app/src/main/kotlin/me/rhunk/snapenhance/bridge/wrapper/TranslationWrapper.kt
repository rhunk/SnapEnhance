package me.rhunk.snapenhance.bridge.wrapper

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.data.LocalePair
import java.util.Locale


class TranslationWrapper {
    companion object {
        private const val DEFAULT_LOCALE = "en_US"

        fun fetchLocales(context: Context): List<LocalePair> {
            val deviceLocale = Locale.getDefault().toString()
            val locales = mutableListOf<LocalePair>()

            locales.add(LocalePair(DEFAULT_LOCALE, context.resources.assets.open("lang/$DEFAULT_LOCALE.json").bufferedReader().use { it.readText() }))

            if (deviceLocale == DEFAULT_LOCALE) return locales

            val compatibleLocale = context.resources.assets.list("lang")?.firstOrNull { it.startsWith(deviceLocale) }?.substring(0, 5) ?: return locales

            context.resources.assets.open("lang/$compatibleLocale.json").use { inputStream ->
                locales.add(LocalePair(compatibleLocale, inputStream.bufferedReader().use { it.readText() }))
            }

            return locales
        }
    }


    private val translationMap = linkedMapOf<String, String>()
    private lateinit var _locale: String

    val locale by lazy {
        Locale(_locale.substring(0, 2), _locale.substring(3, 5))
    }

    private fun load(localePair: LocalePair) {
        if (!::_locale.isInitialized) {
            _locale = localePair.locale
        }

        val translations = JsonParser.parseString(localePair.content).asJsonObject
        if (translations == null || translations.isJsonNull) {
            return
        }

        fun scanObject(jsonObject: JsonObject, prefix: String = "") {
            jsonObject.entrySet().forEach {
                if (it.value.isJsonPrimitive) {
                    val key = "$prefix${it.key}"
                    translationMap[key] = it.value.asString
                }
                if (!it.value.isJsonObject) return@forEach
                scanObject(it.value.asJsonObject, "$prefix${it.key}.")
            }
        }

        scanObject(translations)
    }

    fun loadFromBridge(bridgeClient: BridgeClient) {
        bridgeClient.fetchTranslations().forEach {
            load(it)
        }
    }

    fun loadFromContext(context: Context) {
        fetchLocales(context).forEach {
            load(it)
        }
    }

    operator fun get(key: String): String {
        return translationMap[key] ?: key.also { Logger.debug("Missing translation for $key") }
    }

    fun format(key: String, vararg args: Pair<String, String>): String {
        return args.fold(get(key)) { acc, pair ->
            acc.replace("{${pair.first}}", pair.second)
        }
    }

    fun getCategory(key: String): TranslationWrapper {
        return TranslationWrapper().apply {
            translationMap.putAll(
                this@TranslationWrapper.translationMap
                    .filterKeys { it.startsWith("$key.") }
                    .mapKeys { it.key.substring(key.length + 1) }
            )
        }
    }
}