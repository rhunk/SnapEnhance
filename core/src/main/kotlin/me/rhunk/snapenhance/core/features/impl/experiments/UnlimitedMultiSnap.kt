package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.setObjectField

class UnlimitedMultiSnap : Feature("UnlimitedMultiSnap", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        android.util.Pair::class.java.hookConstructor(HookStage.AFTER, {
            context.config.experimental.unlimitedMultiSnap.get()
        }) { param ->
            val first = param.argNullable<Any>(0)
            val second = param.argNullable<Any>(1)
            if (
                first == true && // isOverTheLimit
                second == 8 // limit
            ) {
                param.thisObject<Any>().setObjectField("first", false)
            }
        }
    }
}