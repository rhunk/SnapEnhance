package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class SnapchatPlus: Feature("SnapchatPlus", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.bool(ConfigProperty.SNAPCHAT_PLUS)) return

        Hooker.hookConstructor(context.mappings.getMappedClass("SubscriptionInfoClass"), HookStage.BEFORE) { param ->
            //check if the user is already premium
            if (param.arg(0) as Int == 2) {
                return@hookConstructor
            }
            //subscription info tier
            param.setArg(0, 2)
            //subscription status
            param.setArg(1, 2)
            //subscription time
            param.setArg(2, System.currentTimeMillis() - 7776000000L)
            //expiration time
            param.setArg(3, System.currentTimeMillis() + 15552000000L)
        }
    }
}