package me.rhunk.snapenhance.features.impl.tweaks

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import me.rhunk.snapenhance.data.wrapper.impl.ScSize
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.hook.hookConstructor

class CameraTweaks : Feature("Camera Tweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    companion object {
        val resolutions = listOf("3264x2448", "3264x1840", "3264x1504", "2688x1512", "2560x1920", "2448x2448", "2340x1080", "2160x1080", "1920x1440", "1920x1080", "1600x1200", "1600x960", "1600x900", "1600x736", "1600x720", "1560x720", "1520x720", "1440x1080", "1440x720", "1280x720", "1080x1080", "1080x720", "960x720", "720x720", "720x480", "640x480", "352x288", "320x240", "176x144")
    }

    private fun parseResolution(resolution: String): IntArray {
        return resolution.split("x").map { it.toInt() }.toIntArray()
    }

    @SuppressLint("MissingPermission", "DiscouragedApi")
    override fun onActivityCreate() {
        if (context.config.camera.disable.get()) {
            ContextWrapper::class.java.hook("checkPermission", HookStage.BEFORE) { param ->
                val permission = param.arg<String>(0)
                if (permission == Manifest.permission.CAMERA) {
                    param.setResult(PackageManager.PERMISSION_GRANTED)
                }
            }

            CameraManager::class.java.hook("openCamera", HookStage.BEFORE) { param ->
                param.setResult(null)
            }
        }

        val previewResolutionConfig = context.config.camera.overridePreviewResolution.getNullable()?.let { parseResolution(it) }
        val captureResolutionConfig = context.config.camera.overridePictureResolution.getNullable()?.let { parseResolution(it) }

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