package me.rhunk.snapenhance.features.impl.tweaks

import android.app.AlertDialog
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import java.lang.reflect.Modifier

class GooglePlayServicesDialogs : Feature("Disable GMS Dialogs", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.bool(ConfigProperty.DISABLE_GOOGLE_PLAY_DIALOGS)) return

        findClass("com.google.android.gms.common.GoogleApiAvailability").methods
            .first { Modifier.isStatic(it.modifiers) && it.returnType == AlertDialog::class.java }.let { method ->
            method.hook(HookStage.BEFORE) { param ->
                Logger.debug("GoogleApiAvailability.showErrorDialogFragment() called, returning null")
                param.setResult(null)
            }
        }
    }
}