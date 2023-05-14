package me.rhunk.snapenhance.features.impl.spy

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.getObjectField

class AnonymousStoryViewing : Feature("Anonymous Story Viewing", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(context.classCache.networkApi,"submit", HookStage.BEFORE, { context.config.bool(ConfigProperty.ANONYMOUS_STORY_VIEW) }) {
            val httpRequest: Any = it.arg(0)
            val url = httpRequest.getObjectField("mUrl") as String
            if (url.endsWith("readreceipt-indexer/batchuploadreadreceipts") || url.endsWith("v2/batch_cta")) {
                it.setResult(null)
            }
        }
    }
}
