package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class RootConfig : ConfigContainer() {
    val downloader = container("downloader", DownloaderConfig())
    val userInterface = container("user_interface", UserInterfaceTweaks())
    val messaging = container("messaging", MessagingTweaks())
    val global = container("global", Global())
    val camera = container("camera", Camera())
    val experimental = container("experimental", Experimental())
    val spoof = container("spoof", Spoof())
}