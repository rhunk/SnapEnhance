package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.ConfigFlag
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.features.impl.tweaks.CameraTweaks

class Camera : ConfigContainer() {
    val disable = boolean("disable_camera")
    val immersiveCameraPreview = boolean("immersive_camera_preview") { addNotices(FeatureNotice.MAY_CAUSE_CRASHES) }
    val overridePreviewResolution = unique("override_preview_resolution", *CameraTweaks.resolutions.toTypedArray())
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val overridePictureResolution = unique("override_picture_resolution", *CameraTweaks.resolutions.toTypedArray())
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val forceHighestFrameRate = boolean("force_highest_frame_rate") { addNotices(FeatureNotice.MAY_BREAK_INTERNAL_BEHAVIOR) }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
}
