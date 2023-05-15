package me.rhunk.snapenhance.config

enum class ConfigCategory(
    val key: String
) {
    GENERAL("category.general"),
    SPY("category.spy"),
    MEDIA_DOWNLOADER("category.media_download"),
    PRIVACY("category.privacy"),
    UI("category.ui"),
    EXTRAS("category.extras"),
    TWEAKS("category.tweaks"),
    EXPERIMENTAL("category.experimental");
}
