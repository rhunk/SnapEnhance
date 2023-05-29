package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class MeoPasscodeBypass : Feature("Meo Passcode Bypass", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(
            context.mappings.getMappedClass("BCryptClass"),
            context.mappings.getMappedValue("BCryptClassHashMethod"),
            HookStage.BEFORE,
            { context.config.bool(ConfigProperty.MEO_PASSCODE_BYPASS) },
        ) { param ->
            //set the hash to the result of the method
            param.setResult(param.arg(1))
        }
    }
}