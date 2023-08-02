package me.rhunk.snapenhance.ui.setup

import android.content.Context
import me.rhunk.snapenhance.core.config.ModConfig

class SetupContext(
    private val context: Context
) {
    val config = ModConfig()

    init {
        config.loadFromContext(context)
    }
}