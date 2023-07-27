package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class MeoPasscodeBypass : Feature("Meo Passcode Bypass", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val bcrypt = context.mappings.getMappedMap("BCrypt")

        Hooker.hook(
            context.androidContext.classLoader.loadClass(bcrypt["class"].toString()),
            bcrypt["hashMethod"].toString(),
            HookStage.BEFORE,
            { context.config.bool(ConfigProperty.MEO_PASSCODE_BYPASS) },
        ) { param ->
            //set the hash to the result of the method
            param.setResult(param.arg(1))
        }
    }
}