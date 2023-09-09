package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor

class UnlimitedMultiSnap : Feature("UnlimitedMultiSnap", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        android.util.Pair::class.java.hookConstructor(HookStage.AFTER, {
            context.config.experimental.unlimitedMultiSnap.get()
        }) { param ->
            val first = param.arg<Any>(0)
            val second = param.arg<Any>(1)
            if (
                first == true && // isOverTheLimit
                second == 8 // limit
            ) {
                param.thisObject<Any>().setObjectField("first", false)
            }
        }
    }
}