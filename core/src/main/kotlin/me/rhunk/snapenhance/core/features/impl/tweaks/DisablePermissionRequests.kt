package me.rhunk.snapenhance.core.features.impl.tweaks

import android.content.ContextWrapper
import android.content.pm.PackageManager
import me.rhunk.snapenhance.common.config.impl.Global
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class DisablePermissionRequests : Feature("Disable Permission Requests", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val deniedPermissions by context.config.global.disablePermissionRequests
        if (deniedPermissions.isEmpty()) return

        ContextWrapper::class.java.hook("checkPermission", HookStage.BEFORE) { param ->
            val permission = param.arg<String>(0)
            val permissionKey = Global.permissionMap[permission] ?: return@hook
            if (deniedPermissions.contains(permissionKey)) {
                param.setResult(PackageManager.PERMISSION_GRANTED)
            }
        }
    }
}