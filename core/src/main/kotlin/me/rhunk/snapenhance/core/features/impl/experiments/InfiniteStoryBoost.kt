package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.mapper.impl.StoryBoostStateMapper

class InfiniteStoryBoost : Feature("InfiniteStoryBoost", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.experimental.infiniteStoryBoost.get()) return

        context.mappings.useMapper(StoryBoostStateMapper::class) {
            classReference.get()?.hookConstructor(HookStage.BEFORE) { param ->
                val startTimeMillis = param.arg<Long>(1)
                //reset timestamp if it's more than 24 hours
                if (System.currentTimeMillis() - startTimeMillis > 86400000) {
                    param.setArg(1, 0)
                    param.setArg(2, 0)
                }
            }
        }
    }
}