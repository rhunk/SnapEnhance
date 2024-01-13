package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.mapper.impl.ScoreUpdateMapper
import java.lang.reflect.Constructor

class NoFriendScoreDelay : Feature("NoFriendScoreDelay", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.experimental.noFriendScoreDelay.get()) return

        context.mappings.useMapper(ScoreUpdateMapper::class) {
            classReference.get()?.hookConstructor(HookStage.BEFORE) { param ->
                val constructor = param.method() as Constructor<*>
                if (constructor.parameterTypes.size < 3 || constructor.parameterTypes[3] != java.util.Collection::class.java) return@hookConstructor
                param.setArg(2, 0L)
            }
        }
    }
}