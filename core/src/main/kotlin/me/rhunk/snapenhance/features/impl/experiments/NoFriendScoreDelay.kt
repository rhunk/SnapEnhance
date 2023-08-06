package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor
import java.lang.reflect.Constructor

class NoFriendScoreDelay : Feature("NoFriendScoreDelay", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.experimental.noFriendScoreDelay.get()) return
        val scoreUpdateClass = context.mappings.getMappedClass("ScoreUpdate")

        scoreUpdateClass.hookConstructor(HookStage.BEFORE) { param ->
            val constructor = param.method() as Constructor<*>
            if (constructor.parameterTypes.size < 3 || constructor.parameterTypes[3] != java.util.Collection::class.java) return@hookConstructor
            param.setArg(2, 0L)
        }
    }
}