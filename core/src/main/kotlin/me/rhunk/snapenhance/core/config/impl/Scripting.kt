package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.ConfigFlag

class Scripting : ConfigContainer() {
    val moduleFolder = string("module_folder", "modules") { addFlags(ConfigFlag.FOLDER)  }
    val hotReload = boolean("hot_reload", false)
}