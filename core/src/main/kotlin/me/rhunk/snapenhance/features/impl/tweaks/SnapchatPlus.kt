package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class SnapchatPlus: Feature("SnapchatPlus", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val originalSubscriptionTime = (System.currentTimeMillis() - 7776000000L)
    private val expirationTimeMillis = (System.currentTimeMillis() + 15552000000L)

    override fun asyncOnActivityCreate() {
        if (!context.config.global.snapchatPlus.get()) return

        val subscriptionInfoClass = context.mappings.getMappedClass("SubscriptionInfoClass")

        Hooker.hookConstructor(subscriptionInfoClass, HookStage.BEFORE) { param ->
            if (param.arg<Int>(0) == 2) return@hookConstructor
            //subscription tier
            param.setArg(0, 2)
            //subscription status
            param.setArg(1, 2)

            param.setArg(2, originalSubscriptionTime)
            param.setArg(3, expirationTimeMillis)
        }
    }
}