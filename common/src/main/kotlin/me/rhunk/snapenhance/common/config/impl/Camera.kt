package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.ConfigFlag
import me.rhunk.snapenhance.common.config.FeatureNotice

class Camera : ConfigContainer() {
    companion object {
        val resolutions = listOf("3264x2448", "3264x1840", "3264x1504", "2688x1512", "2560x1920", "2448x2448", "2340x1080", "2160x1080", "1920x1440", "1920x1080", "1600x1200", "1600x960", "1600x900", "1600x736", "1600x720", "1560x720", "1520x720", "1440x1080", "1440x720", "1280x720", "1080x1080", "1080x720", "960x720", "720x720", "720x480", "640x480", "352x288", "320x240", "176x144").toTypedArray()
    }

    val disable = boolean("disable_camera")
    val immersiveCameraPreview = boolean("immersive_camera_preview") { addNotices(FeatureNotice.UNSTABLE) }
    val overridePreviewResolution = unique("override_preview_resolution", *resolutions)
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val overridePictureResolution = unique("override_picture_resolution", *resolutions)
        { addFlags(ConfigFlag.NO_TRANSLATE) }
    val customFrameRate = unique("custom_frame_rate",
        "5", "10", "20", "25", "30", "48", "60", "90", "120"
    ) { addNotices(FeatureNotice.UNSTABLE); addFlags(ConfigFlag.NO_TRANSLATE) }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
}
