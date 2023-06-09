package me.rhunk.snapenhance.features.impl.tweaks

import android.app.admin.DevicePolicyManager
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class CameraDisabler : Feature("Camera Disabler", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.bool(ConfigProperty.CAMERA_DISABLE)) return
        //TODO: (?) Implement a hook that hooks the camera permission check instead of this
        Hooker.hook(DevicePolicyManager::class.java, "getCameraDisabled", HookStage.BEFORE) {
            it.setResult(true)
        }
    }
}