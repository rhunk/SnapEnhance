package me.rhunk.snapenhance.core.features.impl

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.common.data.StoryData
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import java.nio.ByteBuffer
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Stories : Feature("Stories", loadParams = FeatureLoadParams.INIT_SYNC) {
    @OptIn(ExperimentalEncodingApi::class)
    override fun init() {
        val disablePublicStories by context.config.global.disablePublicStories
        val storyLogger by context.config.experimental.storyLogger

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
                    ProtoEditor(buffer).apply {
                        edit {
                            get(2).removeIf {
                                it.toReader().getVarInt(7, 4) == 1L
                            }
                        }
                    }.toByteArray()
                }
                return@subscribe
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

            if (storyLogger && event.url.endsWith("df-mixer-prod/soma/batch_stories")) {
                event.onSuccess { buffer ->
                    val stories = mutableMapOf<String, MutableList<StoryData>>()
                    val reader = ProtoReader(buffer ?: return@onSuccess)
                    reader.followPath(3, 3) {
                        eachBuffer(3) {
                            followPath(36) {
                                eachBuffer(1) data@{
                                    val userId = getString(8, 1) ?: return@data

                                    stories.getOrPut(userId) {
                                        mutableListOf()
                                    }.add(StoryData(
                                        url = getString(2, 2)?.substringBefore("?") ?: return@data,
                                        postedAt = getVarInt(3) ?: -1L,
                                        createdAt = getVarInt(27) ?: -1L,
                                        key = Base64.decode(getString(2, 5) ?: return@data),
                                        iv = Base64.decode(getString(2, 4) ?: return@data)
                                    ))
                                }
                            }
                        }
                    }

                    context.coroutineScope.launch {
                        stories.forEach { (userId, stories) ->
                            stories.forEach { story ->
                                context.bridgeClient.getMessageLogger().addStory(userId, story.url, story.postedAt, story.createdAt, story.key, story.iv)
                            }
                        }
                    }
                }

                return@subscribe
            }
        }
    }
}