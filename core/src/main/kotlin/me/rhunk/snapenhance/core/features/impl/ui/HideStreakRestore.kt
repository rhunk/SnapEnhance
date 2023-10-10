package me.rhunk.snapenhance.core.features.impl.ui

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField

class HideStreakRestore : Feature("HideStreakRestore", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.userInterface.hideStreakRestore.get()) return

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val streakMetadata = param.thisObject<Any>().getObjectField("mStreakMetadata") ?: return@hookConstructor
            streakMetadata.setObjectField("mExpiredStreak", null)
        }
    }
}