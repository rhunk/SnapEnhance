package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.ConfigFlag

class Scripting : ConfigContainer() {
    val developerMode = boolean("developer_mode", false) { requireRestart() }
    val moduleFolder = string("module_folder", "modules") { addFlags(ConfigFlag.FOLDER); requireRestart()  }
    val hotReload = boolean("hot_reload", false)
}