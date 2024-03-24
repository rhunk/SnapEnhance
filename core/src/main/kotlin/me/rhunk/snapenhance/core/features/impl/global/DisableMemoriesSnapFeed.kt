package me.rhunk.snapenhance.core.features.impl.global

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.mapper.impl.MemoriesPresenterMapper

class DisableMemoriesSnapFeed : Feature("Disable Memories Snap Feed", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.global.disableMemoriesSnapFeed.get()) return

        context.mappings.useMapper(MemoriesPresenterMapper::class) {
            classReference.get()?.apply {
                val getNameMethod = getMethod("getName") ?: return@apply

                hook(onNavigationEventMethod.get()!!, HookStage.BEFORE) { param ->
                    val instance = param.thisObject<Any>()

                    if (getNameMethod.invoke(instance) == "MemoriesAsyncPresenterFragmentSubscriber") {
                        param.setResult(null)
                    }
                }
            }
        }
    }
}