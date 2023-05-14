package me.rhunk.snapenhance.manager.impl

import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.manager.Manager
import java.util.*

class TranslationManager(
    private val context: ModContext
) : Manager {
    override fun init() {

    }

    fun getLocale(): Locale = Locale.getDefault()

    fun get(key: String): String {
        return key
    }
}