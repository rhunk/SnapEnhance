package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.ConfigFlag
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.features.impl.tweaks.CameraTweaks

class Camera : ConfigContainer() {
    val disable = boolean("disable_camera")
    val immersiveCameraPreview = boolean("immersive_camera_preview") { addNotices(FeatureNotice.UNSTABLE) }
    val overridePreviewResolution = unique("override_preview_resolution", *CameraTweaks.resolutions.toTypedArray())
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val overridePictureResolution = unique("override_picture_resolution", *CameraTweaks.resolutions.toTypedArray())
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val customFrameRate = unique("custom_frame_rate",
        "5", "10", "20", "25", "30", "48", "60", "90", "120"
    ) { addNotices(FeatureNotice.UNSTABLE); addFlags(ConfigFlag.NO_TRANSLATE) }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
}
