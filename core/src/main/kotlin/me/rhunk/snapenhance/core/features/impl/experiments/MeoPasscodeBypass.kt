package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.mapper.impl.BCryptClassMapper

class MeoPasscodeBypass : Feature("Meo Passcode Bypass", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.experimental.meoPasscodeBypass.get()) return

        context.mappings.useMapper(BCryptClassMapper::class) {
            classReference.get()?.hook(
                hashMethod.get()!!,
                HookStage.BEFORE,
            ) { param ->
                //set the hash to the result of the method
                param.setResult(param.arg(1))
            }
        }
    }
}