package me.rhunk.snapenhance.config

enum class ConfigCategory(
    val key: String,
    val hidden: Boolean = false
) {
    SPYING_PRIVACY("spying_privacy"),
    MEDIA_MANAGEMENT("media_manager"),
    UI_TWEAKS("ui_tweaks"),
    UPDATES("updates"),
    CAMERA("camera"),
    EXPERIMENTAL_DEBUGGING("experimental_debugging"),
    DEVICE_SPOOFER("device_spoofer", hidden = true)
}
