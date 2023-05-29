package me.rhunk.snapenhance.config

enum class ConfigCategory(
    val key: String
) {
    GENERAL("category.general"),
    SPYING("category.spying"),
    MEDIA_DOWNLOADER("category.media_download"),
    PRIVACY("category.privacy"),
    UI("category.ui"),
    EXTRAS("category.extras"),
    TWEAKS("category.tweaks"),
    EXPERIMENTAL("category.experimental");
}
