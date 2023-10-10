package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor

class InfiniteStoryBoost : Feature("InfiniteStoryBoost", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val storyBoostStateClass = context.mappings.getMappedClass("StoryBoostStateClass")

        storyBoostStateClass.hookConstructor(HookStage.BEFORE, {
            context.config.experimental.infiniteStoryBoost.get()
        }) { param ->
            val startTimeMillis = param.arg<Long>(1)
            //reset timestamp if it's more than 24 hours
            if (System.currentTimeMillis() - startTimeMillis > 86400000) {
                param.setArg(1, 0)
                param.setArg(2, 0)
            }
        }
    }
}