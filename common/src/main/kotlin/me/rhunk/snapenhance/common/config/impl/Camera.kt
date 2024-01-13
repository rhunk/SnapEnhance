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
        private val customFrameRates = arrayOf("5", "10", "20", "25", "30", "48", "60", "90", "120")
    }

    private lateinit var _overrideFrontResolution: PropertyValue<String>
    private lateinit var _overrideBackResolution: PropertyValue<String>

    override fun lateInit(context: Context) {
        val backResolutions = mutableListOf<String>()
        val frontResolutions = mutableListOf<String>()

        context.getSystemService(CameraManager::class.java).apply {
            if (context.packageName == Constants.SNAPCHAT_PACKAGE_NAME) return@apply // prevent snapchat from crashing

            runCatching {
                cameraIdList.forEach { cameraId ->
                    val characteristics = getCameraCharacteristics(cameraId)
                    val isSelfie = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

                    (frontResolutions.takeIf { isSelfie } ?: backResolutions).addAll(
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let {
                            it.outputFormats.flatMap { format -> it.getOutputSizes(format).toList() }
                        }?.sortedByDescending { it.width * it.height }?.map { "${it.width}x${it.height}" }?.distinct() ?: emptyList()
                    )
                }
            }.onFailure {
                AbstractLogger.directError("Failed to get camera resolutions", it)
                backResolutions.addAll(defaultResolutions)
                frontResolutions.addAll(defaultResolutions)
            }
        }

        _overrideFrontResolution = unique("override_front_resolution", *frontResolutions.toTypedArray())
            { addFlags(ConfigFlag.NO_TRANSLATE) }
        _overrideBackResolution = unique("override_back_resolution", *backResolutions.toTypedArray())
            { addFlags(ConfigFlag.NO_TRANSLATE) }
    }

    val disableCameras = multiple("disable_cameras", "front", "back") { addNotices(FeatureNotice.INTERNAL_BEHAVIOR); requireRestart() }
    val immersiveCameraPreview = boolean("immersive_camera_preview") { addNotices(FeatureNotice.UNSTABLE) }
    val blackPhotos = boolean("black_photos")
    val frontCustomFrameRate = unique("front_custom_frame_rate", *customFrameRates) { requireRestart(); addFlags(ConfigFlag.NO_TRANSLATE) }
    val backCustomFrameRate = unique("back_custom_frame_rate", *customFrameRates) { requireRestart(); addFlags(ConfigFlag.NO_TRANSLATE) }
    val hevcRecording = boolean("hevc_recording") { requireRestart() }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
    val overrideFrontResolution get() = _overrideFrontResolution
    val overrideBackResolution get() = _overrideBackResolution

    val customResolution = string("custom_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }
}
