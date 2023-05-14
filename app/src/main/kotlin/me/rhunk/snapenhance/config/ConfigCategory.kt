package me.rhunk.snapenhance.config

enum class ConfigCategory(
    val key: String
) {
    GENERAL("general"),
    SPY("spy"),
    MEDIA_DOWNLOADER("media_download"),
    PRIVACY("privacy"),
    UI("ui"),
    EXTRAS("extras"),
    TWEAKS("tweaks"),
    EXPERIMENTS("experiments");
}
