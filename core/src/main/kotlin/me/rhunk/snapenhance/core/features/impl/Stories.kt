package me.rhunk.snapenhance.core.features.impl

import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import java.nio.ByteBuffer
import kotlin.coroutines.suspendCoroutine

class Stories : Feature("Stories", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val disablePublicStories by context.config.global.disablePublicStories

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            fun cancelRequest() {
                runBlocking {
                    suspendCoroutine {
                        context.httpServer.ensureServerStarted()?.let { server ->
                            event.url = "http://127.0.0.1:${server.port}"
                            it.resumeWith(Result.success(Unit))
                        } ?: run {
                            event.canceled = true
                            it.resumeWith(Result.success(Unit))
                        }
                    }
                }
            }

            if (event.url.endsWith("readreceipt-indexer/batchuploadreadreceipts")) {
                if (context.config.messaging.anonymousStoryViewing.get()) {
                    cancelRequest()
                    return@subscribe
                }
                if (!context.config.messaging.preventStoryRewatchIndicator.get()) return@subscribe
                event.hookRequestBuffer { buffer ->
                    if (ProtoReader(buffer).getVarInt(2, 7, 4) == 1L) {
                        cancelRequest()
                    }
                    buffer
                }
            }

            if (disablePublicStories && (event.url.endsWith("df-mixer-prod/stories") || event.url.endsWith("df-mixer-prod/batch_stories")))  {
                event.onSuccess { buffer ->
                    val payload = ProtoEditor(buffer ?: return@onSuccess).apply {
                        edit(3) { remove(3) }
                    }.toByteArray()
                    setArg(2, ByteBuffer.wrap(payload))
                }
                return@subscribe
            }
        }
    }
}