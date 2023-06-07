package me.rhunk.snapenhance.features.impl.tweaks

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.widget.Toast
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class CameraDisabler : Feature("Camera Disabler", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC){
    override fun asyncOnActivityCreate() {
        //TODO: Implement a hook that hooks the camera permission check instead of this
        val policyManager = DevicePolicyManager::class.java
        if (!context.config.bool(ConfigProperty.CAMERA_DISABLE)) return
        Hooker.hook(policyManager, "getCameraDisabled", HookStage.BEFORE) { param ->
            param.setResult(true)
        }
        
        /*
        val packageManager = PackageManager::class.java
        Hooker.hook(packageManager, "checkSelfPermission", HookStage.BEFORE) { param ->
            val perm = param.args()[0] as String
            Toast.makeText(context.mainActivity!!, perm, Toast.LENGTH_LONG).show() //DEBUG
            if(perm != Manifest.permission.CAMERA) return@hook
            param.setResult(PackageManager.PERMISSION_GRANTED)
        }
        
        val appOpsManager = AppOpsManager::class.java
        Hooker.hook(appOpsManager, "checkOp", HookStage.BEFORE) { param ->
            if(param.args()[0] != AppOpsManager.OPSTR_CAMERA) return@hook
            param.setResult(AppOpsManager.MODE_ALLOWED)
        }
        */
        
    }
    
}