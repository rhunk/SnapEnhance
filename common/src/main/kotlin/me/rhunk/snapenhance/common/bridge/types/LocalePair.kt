package me.rhunk.snapenhance.common.bridge.types

import java.util.Locale

data class LocalePair(
    val locale: String,
    val content: String
) {
    fun getLocale(): Locale {
        if (locale.contains("_")) {
            val split = locale.split("_")
            return Locale(split[0], split[1])
        }
        return Locale(locale)
    }
}