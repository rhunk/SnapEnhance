package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class NativeHooks: ConfigContainer(hasGlobalState = true) {
    val disableBitmoji = boolean("disable_bitmoji")
    val fixGalleryMediaOverride = boolean("fix_gallery_media_override")
}