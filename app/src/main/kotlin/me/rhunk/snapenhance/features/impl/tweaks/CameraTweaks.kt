package me.rhunk.snapenhance.features.impl.tweaks

import android.annotation.SuppressLint
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.wrapper.impl.ScSize
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.ConfigEnumKeys
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor

class CameraTweaks : Feature("Camera Tweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    companion object {
        val resolutions = listOf("OFF", "3264x2448", "3264x1840", "3264x1504", "2688x1512", "2560x1920", "2448x2448", "2340x1080", "2160x1080", "1920x1440", "1920x1080", "1600x1200", "1600x960", "1600x900", "1600x736", "1600x720", "1560x720", "1520x720", "1440x1080", "1440x720", "1280x720", "1080x1080", "1080x720", "960x720", "720x720", "720x480", "640x480", "352x288", "320x240", "176x144")
    }

    private fun parseResolution(resolution: String): IntArray? {
        return resolution.takeIf { resolution != "OFF" }?.split("x")?.map { it.toInt() }?.toIntArray()
    }

    @SuppressLint("MissingPermission", "DiscouragedApi")
    override fun onActivityCreate() {
        ConfigEnumKeys.hookAllEnums(context.mappings.getMappedClass("enums", "CAMERA")) {
            if (key == "FORCE_CAMERA_HIGHEST_FPS" && context.config.bool(ConfigProperty.FORCE_HIGHEST_FRAME_RATE)) {
                set(true)
            }
            if (key == "MEDIA_RECORDER_MAX_QUALITY_LEVEL" && context.config.bool(ConfigProperty.FORCE_CAMERA_SOURCE_ENCODING)) {
                value!!.javaClass.enumConstants?.let { enumData -> set(enumData.filter { it.toString() == "LEVEL_MAX" }) }
            }
        }

        val previewResolutionConfig = parseResolution(context.config.state(ConfigProperty.OVERRIDE_PREVIEW_RESOLUTION))
        val captureResolutionConfig = parseResolution(context.config.state(ConfigProperty.OVERRIDE_PICTURE_RESOLUTION))

        context.mappings.getMappedClass("ScCameraSettings").hookConstructor(HookStage.BEFORE) { param ->
            val previewResolution = ScSize(param.argNullable(2))
            val captureResolution = ScSize(param.argNullable(3))

            if (previewResolution.isPresent() && captureResolution.isPresent()) {
                previewResolutionConfig?.let {
                    previewResolution.first = it[0]
                    previewResolution.second = it[1]
                }

                captureResolutionConfig?.let {
                    captureResolution.first = it[0]
                    captureResolution.second = it[1]
                }
            }
        }
    }
}