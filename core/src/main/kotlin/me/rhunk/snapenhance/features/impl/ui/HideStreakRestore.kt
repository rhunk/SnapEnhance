package me.rhunk.snapenhance.features.impl.ui

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor

class HideStreakRestore : Feature("HideStreakRestore", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.userInterface.hideStreakRestore.get()) return

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val streakMetadata = param.thisObject<Any>().getObjectField("mStreakMetadata") ?: return@hookConstructor
            streakMetadata.setObjectField("mExpiredStreak", null)
        }
    }
}