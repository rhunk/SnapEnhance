package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class StreaksReminderConfig : ConfigContainer(hasGlobalState = true) {
    val interval = integer("interval", 2)
}