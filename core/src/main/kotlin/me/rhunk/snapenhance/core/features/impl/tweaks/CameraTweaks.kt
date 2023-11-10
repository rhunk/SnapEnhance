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
import android.util.Range
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.impl.ScSize
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

            CameraManager::class.java.hook("openCamera", HookStage.BEFORE) { param ->
                param.setResult(null)
            }
        }

        val previewResolutionConfig = config.customPreviewResolution.getNullable()?.takeIf { it.isNotEmpty() }?.let { parseResolution(it) }
            ?: config.overridePreviewResolution.getNullable()?.let { parseResolution(it) }
        val captureResolutionConfig = config.customPictureResolution.getNullable()?.takeIf { it.isNotEmpty() }?.let { parseResolution(it) }
            ?: config.overridePictureResolution.getNullable()?.let { parseResolution(it) }

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

        if (config.blackPhotos.get()) {
            findClass("android.media.ImageReader\$SurfaceImage").hook("getPlanes", HookStage.AFTER) { param ->
                val image = param.thisObject() as? Image ?: return@hook
                val planes = param.getResult() as? Array<*> ?: return@hook
                val output = ByteArrayOutputStream()
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
                    compress(Bitmap.CompressFormat.JPEG, 100, output)
                    recycle()
                }
                planes.filterNotNull().forEach {  plane ->
                    plane.setObjectField("mBuffer", ByteBuffer.wrap(output.toByteArray()))
                }
            }
        }
    }
}