package me.rhunk.snapenhance.features.impl.privacy

import android.database.ContentObserver
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class PreventScreenshotDetections : Feature("Prevent Screenshot Detections", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(ContentObserver::class.java,"dispatchChange", HookStage.BEFORE, { context.config.bool(ConfigProperty.PREVENT_SCREENSHOTS) }) {
            it.setResult(null)
        }
    }
}