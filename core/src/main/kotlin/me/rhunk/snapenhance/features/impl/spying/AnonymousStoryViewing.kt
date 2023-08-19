package me.rhunk.snapenhance.features.impl.spying

import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.ktx.getObjectField
import me.rhunk.snapenhance.util.ktx.setObjectField

class AnonymousStoryViewing : Feature("Anonymous Story Viewing", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val anonymousStoryViewProperty by context.config.messaging.anonymousStoryViewing
        Hooker.hook(context.classCache.networkApi,"submit", HookStage.BEFORE, { anonymousStoryViewProperty }) {
            val httpRequest: Any = it.arg(0)
            val url = httpRequest.getObjectField("mUrl") as String
            if (url.endsWith("readreceipt-indexer/batchuploadreadreceipts")) {
                httpRequest.setObjectField("mUrl", "http://127.0.0.1")
            }
        }
    }
}
