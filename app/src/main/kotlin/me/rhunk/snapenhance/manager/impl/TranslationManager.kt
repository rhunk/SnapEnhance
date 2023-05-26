package me.rhunk.snapenhance.manager.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.manager.Manager
import java.util.Locale

class TranslationManager(
    private val context: ModContext
) : Manager {
    private val translationMap = mutableMapOf<String, String>()
    lateinit var locale: Locale

    override fun init() {
        val messageLocaleResult = context.bridgeClient.fetchTranslations();
        locale = messageLocaleResult.locale?.split("_")?.let { Locale(it[0], it[1]) } ?: Locale.getDefault()

        val translations = JsonParser.parseString(messageLocaleResult.content?.toString(Charsets.UTF_8)).asJsonObject
        if (translations == null || translations.isJsonNull) {
            context.crash("Failed to fetch translations")
            return
        }

        fun scanObject(jsonObject: JsonObject, prefix: String = "") {
            jsonObject.entrySet().forEach {
                if (it.value.isJsonPrimitive) {
                    translationMap["$prefix${it.key}"] = it.value.asString
                }
                if (!it.value.isJsonObject) return@forEach
                scanObject(it.value.asJsonObject, "$prefix${it.key}.")
            }
        }

        scanObject(translations)
    }


    fun get(key: String): String {
        return translationMap[key] ?: key.also { Logger.xposedLog("Missing translation for $key") }
    }
}