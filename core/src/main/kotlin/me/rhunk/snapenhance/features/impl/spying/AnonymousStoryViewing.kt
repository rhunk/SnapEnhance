package me.rhunk.snapenhance.features.impl.spying

import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.eventbus.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.util.download.HttpServer
import kotlin.coroutines.suspendCoroutine

class AnonymousStoryViewing : Feature("Anonymous Story Viewing", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val anonymousStoryViewProperty by context.config.messaging.anonymousStoryViewing
        val httpServer = HttpServer()

        context.event.subscribe(NetworkApiRequestEvent::class, { anonymousStoryViewProperty }) { event ->
            if (!event.url.endsWith("readreceipt-indexer/batchuploadreadreceipts")) return@subscribe
            runBlocking {
                suspendCoroutine {
                    httpServer.ensureServerStarted {
                        event.url = "http://127.0.0.1:${httpServer.port}"
                        it.resumeWith(Result.success(Unit))
                    }
                }
            }
        }
    }
}
