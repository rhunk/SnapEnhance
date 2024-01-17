package me.rhunk.snapenhance.core.features.impl.ui

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.dataBuilder
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor

class HideStreakRestore : Feature("HideStreakRestore", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        if (!context.config.userInterface.hideStreakRestore.get()) return

        findClass("com.snapchat.client.messaging.StreakMetadata").hookConstructor(HookStage.AFTER) { param ->
            param.thisObject<Any>().dataBuilder {
                val currentTimeMillis = System.currentTimeMillis()
                val expiration = get<Long>("mExpirationTimestampMs") ?: return@hookConstructor
                set("mExpiredStreak", null)
                if (expiration < currentTimeMillis) {
                    set("mExpirationTimestampMs", currentTimeMillis + 60000L)
                }
            }
        }
    }
}