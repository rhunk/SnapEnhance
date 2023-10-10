package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker

class MeoPasscodeBypass : Feature("Meo Passcode Bypass", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val bcrypt = context.mappings.getMappedMap("BCrypt")

        Hooker.hook(
            context.androidContext.classLoader.loadClass(bcrypt["class"].toString()),
            bcrypt["hashMethod"].toString(),
            HookStage.BEFORE,
            { context.config.experimental.meoPasscodeBypass.get() },
        ) { param ->
            //set the hash to the result of the method
            param.setResult(param.arg(1))
        }
    }
}