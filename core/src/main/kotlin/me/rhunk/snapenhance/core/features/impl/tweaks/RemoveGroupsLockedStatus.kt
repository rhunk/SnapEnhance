package me.rhunk.snapenhance.core.features.impl.tweaks

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.dataBuilder
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor

class RemoveGroupsLockedStatus : Feature("Remove Groups Locked Status", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.messaging.removeGroupsLockedStatus.get()) return
        context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
            param.thisObject<Any>().dataBuilder {
                set("mLockedState", "UNLOCKED")
            }
        }
    }
}