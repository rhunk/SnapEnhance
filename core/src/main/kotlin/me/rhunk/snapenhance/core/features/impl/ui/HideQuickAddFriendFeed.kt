package me.rhunk.snapenhance.core.features.impl.ui

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.setObjectField

class HideQuickAddFriendFeed : Feature("HideQuickAddFriendFeed", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.userInterface.hideQuickAddFriendFeed.get()) return

        val friendingDataSource = context.mappings.getMappedMap("FriendingDataSources")
        findClass(friendingDataSource["class"].toString()).hookConstructor(HookStage.AFTER) { param ->
            param.thisObject<Any>().setObjectField(
                friendingDataSource["quickAddSourceListField"].toString(),
                arrayListOf<Any>()
            )
        }
    }
}