package me.rhunk.snapenhance.core.features.impl.tweaks

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.Key
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.util.Range
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class CameraTweaks : Feature("Camera Tweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {

    private fun parseResolution(resolution: String): IntArray? {
        return runCatching { resolution.split("x").map { it.toInt() }.toIntArray() }.getOrNull()
    }

    @SuppressLint("MissingPermission", "DiscouragedApi")
    override fun onActivityCreate() {
        val config = context.config.camera
        if (config.disable.get()) {
            ContextWrapper::class.java.hook("checkPermission", HookStage.BEFORE) { param ->
                val permission = param.arg<String>(0)
                if (permission == Manifest.permission.CAMERA) {
                    param.setResult(PackageManager.PERMISSION_GRANTED)
                }
            }

        }

        var isLastCameraFront = false

        CameraManager::class.java.hook("openCamera", HookStage.BEFORE) { param ->
            if (config.disable.get()) {
                param.setResult(null)
                return@hook
            }
            val cameraManager = param.thisObject() as? CameraManager ?: return@hook
            isLastCameraFront = cameraManager.getCameraCharacteristics(param.arg(0)).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }

        ImageReader::class.java.hook("newInstance", HookStage.BEFORE) { param ->
            val captureResolutionConfig = config.customResolution.getNullable()?.takeIf { it.isNotEmpty() }?.let { parseResolution(it) }
                ?: (if (isLastCameraFront) config.overrideFrontResolution.getNullable() else config.overrideBackResolution.getNullable())?.let { parseResolution(it) } ?: return@hook
            param.setArg(0, captureResolutionConfig[0])
            param.setArg(1, captureResolutionConfig[1])
        }

        config.customFrameRate.getNullable()?.also { value ->
            val customFrameRate = value.toInt()
            CameraCharacteristics::class.java.hook("get", HookStage.AFTER)  { param ->
                val key = param.arg<Key<*>>(0)
                if (key == CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) {
                    val fpsRanges = param.getResult() as? Array<*> ?: return@hook
                    fpsRanges.forEach {
                        val range = it as? Range<*> ?: return@forEach
                        range.setObjectField("mUpper", customFrameRate)
                        range.setObjectField("mLower", customFrameRate)
                    }
                }
            }
        }

        if (config.blackPhotos.get()) {
            findClass("android.media.ImageReader\$SurfaceImage").hook("getPlanes", HookStage.AFTER) { param ->
                val image = param.thisObject() as? Image ?: return@hook
                val planes = param.getResult() as? Array<*> ?: return@hook
                val output = ByteArrayOutputStream()
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
                    compress(Bitmap.CompressFormat.JPEG, 100, output)
                    recycle()
                }
                planes.filterNotNull().forEach { plane ->
                    plane.setObjectField("mBuffer", ByteBuffer.wrap(output.toByteArray()))
                }
            }
        }
    }
}