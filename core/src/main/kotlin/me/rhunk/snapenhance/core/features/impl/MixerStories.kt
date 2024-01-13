package me.rhunk.snapenhance.core.features.impl

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.common.data.StoryData
import me.rhunk.snapenhance.common.data.MixerStoryType
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import java.nio.ByteBuffer
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MixerStories : Feature("MixerStories", loadParams = FeatureLoadParams.INIT_SYNC) {
    @OptIn(ExperimentalEncodingApi::class)
    override fun init() {
        val disableDiscoverSections by context.config.global.disableStorySections

        fun canRemoveDiscoverSection(id: Int): Boolean {
            val storyType = MixerStoryType.fromIndex(id)
            return (storyType == MixerStoryType.SUBSCRIPTIONS && disableDiscoverSections.contains("following")) ||
                    (storyType == MixerStoryType.DISCOVER && disableDiscoverSections.contains("discover")) ||
                    (storyType == MixerStoryType.FRIENDS && disableDiscoverSections.contains("friends"))
        }

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

            if (event.url.endsWith("df-mixer-prod/stories") ||
                event.url.endsWith("df-mixer-prod/batch_stories") ||
                event.url.endsWith("df-mixer-prod/soma/stories") ||
                event.url.endsWith("df-mixer-prod/soma/batch_stories")
            )  {
                event.onSuccess { buffer ->
                    val editor = ProtoEditor(buffer ?: return@onSuccess)
                    editor.edit {
                        editEach(3) {
                            val sectionType = firstOrNull(10)?.toReader()?.getVarInt(1)?.toInt() ?: return@editEach

                            if (sectionType == MixerStoryType.FRIENDS.index && context.config.experimental.storyLogger.get()) {
                                val storyMap = mutableMapOf<String, MutableList<StoryData>>()

                                firstOrNull(3)?.toReader()?.eachBuffer(3) {
                                    followPath(36) {
                                        eachBuffer(1) data@{
                                            val userId = getString(8, 1) ?: return@data

                                            storyMap.getOrPut(userId) {
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

                                context.coroutineScope.launch {
                                    storyMap.forEach { (userId, stories) ->
                                        stories.forEach { story ->
                                            runCatching {
                                                context.bridgeClient.getMessageLogger().addStory(userId, story.url, story.postedAt, story.createdAt, story.key, story.iv)
                                            }.onFailure {
                                                context.log.error("Failed to log story", it)
                                            }
                                        }
                                    }
                                }
                            }

                            if (canRemoveDiscoverSection(sectionType)) {
                                remove(3)
                                addBuffer(3, byteArrayOf())
                            }
                        }
                    }

                    setArg(2, ByteBuffer.wrap(editor.toByteArray()))
                }
                return@subscribe
            }
        }
    }
}