package me.rhunk.snapenhance.config

enum class ConfigCategory(
    val key: String
) {
    SPYING_PRIVACY("category.spying_privacy"),
    MEDIA_MANAGEMENT("category.media_manager"),
    UI_TWEAKS("category.ui_tweaks"),
    UPDATES("category.updates"),
    CAMERA("category.camera"),
    EXPERIMENTAL_DEBUGGING("category.experimental_debugging");
}
