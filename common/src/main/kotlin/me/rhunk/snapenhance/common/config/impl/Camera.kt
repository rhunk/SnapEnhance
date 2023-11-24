package me.rhunk.snapenhance.common.config.impl

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.ConfigFlag
import me.rhunk.snapenhance.common.config.FeatureNotice
import me.rhunk.snapenhance.common.config.PropertyValue
import me.rhunk.snapenhance.common.logger.AbstractLogger

class Camera : ConfigContainer() {
    companion object {
        private val defaultResolutions = listOf("3264x2448", "3264x1840", "3264x1504", "2688x1512", "2560x1920", "2448x2448", "2340x1080", "2160x1080", "1920x1440", "1920x1080", "1600x1200", "1600x960", "1600x900", "1600x736", "1600x720", "1560x720", "1520x720", "1440x1080", "1440x720", "1280x720", "1080x1080", "1080x720", "960x720", "720x720", "720x480", "640x480", "352x288", "320x240", "176x144").toTypedArray()
    }

    private lateinit var _overridePreviewResolution: PropertyValue<String>
    private lateinit var _overridePictureResolution: PropertyValue<String>

    override fun lateInit(context: Context) {
        val resolutions = runCatching {
            if (context.packageName == Constants.SNAPCHAT_PACKAGE_NAME) return@runCatching null // prevent snapchat from crashing
            context.getSystemService(CameraManager::class.java).run {
                cameraIdList.flatMap { cameraId ->
                    getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let {
                        it.outputFormats.flatMap { format -> it.getOutputSizes(format).toList() }
                    } ?: emptyList()
                }.sortedByDescending { it.width * it.height }.map { "${it.width}x${it.height}" }.distinct().toTypedArray()
            }
        }.onFailure {
            AbstractLogger.directError("Failed to get camera resolutions", it)
        }.getOrNull() ?: defaultResolutions

        _overridePreviewResolution = unique("override_preview_resolution", *resolutions)
            { addFlags(ConfigFlag.NO_TRANSLATE) }
        _overridePictureResolution = unique("override_picture_resolution", *resolutions)
            { addFlags(ConfigFlag.NO_TRANSLATE) }
    }

    val disable = boolean("disable_camera")
    val immersiveCameraPreview = boolean("immersive_camera_preview") { addNotices(FeatureNotice.UNSTABLE) }
    val blackPhotos = boolean("black_photos")
    val customFrameRate = unique("custom_frame_rate",
        "5", "10", "20", "25", "30", "48", "60", "90", "120"
    ) { addNotices(FeatureNotice.UNSTABLE); addFlags(ConfigFlag.NO_TRANSLATE) }
    val hevcRecording = boolean("hevc_recording") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
    val overridePreviewResolution get() = _overridePreviewResolution
    val overridePictureResolution get() = _overridePictureResolution
    val customPreviewResolution = string("custom_preview_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }
    val customPictureResolution = string("custom_picture_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }
}
