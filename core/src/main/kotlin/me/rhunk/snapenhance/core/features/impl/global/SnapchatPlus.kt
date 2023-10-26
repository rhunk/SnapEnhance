package me.rhunk.snapenhance.core.features.impl.global

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import me.rhunk.snapenhance.core.util.hook.hook

class SnapchatPlus: Feature("SnapchatPlus", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val originalSubscriptionTime = (System.currentTimeMillis() - 7776000000L)
    private val expirationTimeMillis = (System.currentTimeMillis() + 15552000000L)

    override fun init() {
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

        // optional as ConfigurationOverride does this too
        if (context.config.experimental.hiddenSnapchatPlusFeatures.get()) {
            findClass("com.snap.plus.FeatureCatalog").methods.last {
                !it.name.contains("init") &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].name != "java.lang.Boolean"
            }.hook(HookStage.BEFORE) { param ->
                val instance = param.thisObject<Any>()
                val firstArg = param.arg<Any>(0)

                instance::class.java.declaredFields.filter { it.type == firstArg::class.java }.forEach {
                    it.isAccessible = true
                    it.set(instance, firstArg)
                }
            }
        }
    }
}