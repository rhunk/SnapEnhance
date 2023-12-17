package me.rhunk.snapenhance.core.features.impl.experiments

import android.content.Intent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class PreventForcedLogout : Feature("Prevent Forced Logout", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        if (!context.config.experimental.preventForcedLogout.get()) return
        findClass("com.snap.identity.service.ForcedLogoutBroadcastReceiver").hook("onReceive", HookStage.BEFORE) { param ->
            val intent = param.arg<Intent>(1)
            if (!intent.getBooleanExtra("forced", false)) return@hook
            context.log.verbose("Prevent forced logout, reason=${intent.getStringExtra("reason")}")
            param.setResult(null)
        }
    }
}