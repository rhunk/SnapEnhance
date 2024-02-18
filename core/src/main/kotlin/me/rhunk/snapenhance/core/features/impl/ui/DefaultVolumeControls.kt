package me.rhunk.snapenhance.core.features.impl.ui

import android.view.KeyEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class DefaultVolumeControls : Feature("Default Volume Controls", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.global.defaultVolumeControls.get()) return
        context.mainActivity!!::class.java.hook("onKeyDown", HookStage.BEFORE) { param ->
            val keyCode = param.arg<Int>(0)
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                param.setResult(false)
            }
        }
    }
}