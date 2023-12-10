package me.rhunk.snapenhance.core.features.impl.tweaks

import android.app.Activity
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class BypassScreenshotDetection : Feature("BypassScreenshotDetection", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.messaging.bypassScreenshotDetection.get()) return
        Activity::class.java.hook("registerScreenCaptureCallback", HookStage.BEFORE) { param ->
            param.setResult(null)
        }
        ContentResolver::class.java.methods.first {
            it.name == "registerContentObserver" &&
            it.parameterTypes.contentEquals(arrayOf(android.net.Uri::class.java, Boolean::class.javaPrimitiveType, ContentObserver::class.java))
        }.hook(HookStage.BEFORE) { param ->
            val uri = param.arg<Uri>(0)
            if (uri.host != "media") return@hook
            param.setResult(null)
        }
    }
}