package me.rhunk.snapenhance.core.features.impl.experiments

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rhunk.snapenhance.common.data.download.AudioStreamFormat
import me.rhunk.snapenhance.common.data.download.MediaDownloadSource
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.getObjectFieldOrNull
import me.rhunk.snapenhance.core.util.media.HttpServer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CallRecorder : Feature("Call Recorder", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val httpServer = HttpServer(
        timeout = Integer.MAX_VALUE
    )

    override fun init() {
        if (!context.config.experimental.callRecorder.get()) return

        val streamHandlers = ConcurrentHashMap<Int, MutableList<(data: ByteArray) -> Unit>>() // audioTrack -> handlers
        val participants = CopyOnWriteArrayList<String>()

        findClass("com.snapchat.talkcorev3.CallingSessionState").hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val callingState = instance.getObjectFieldOrNull("mLocalUser")?.getObjectField("mCallingState")

            if (callingState.toString() == "IN_CALL") {
                participants.clear()
                participants.addAll((instance.getObjectField("mParticipants") as Map<*, *>).keys.map { it.toString() })
            }
        }

        AudioTrack::class.java.apply {
            getConstructor(
                AudioAttributes::class.java,
                AudioFormat::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).hook(HookStage.BEFORE) { param ->
                val audioAttributes = param.arg<AudioAttributes>(0)
                if (audioAttributes.usage != AudioAttributes.USAGE_VOICE_COMMUNICATION) return@hook
                val audioFormat = param.arg<AudioFormat>(1)
                val hashCode = param.thisObject<Any>().hashCode()

                lateinit var streamUrl: String
                streamUrl = httpServer.ensureServerStarted()?.putContent(
                    object: HttpServer.HttpContent() {
                        override val contentType: String = "audio/wav"
                        override val chunked: Boolean = true
                        override val contentLength: Long? = null
                        override val newBody: () -> HttpServer.HttpBody = {
                            object: HttpServer.HttpBody() {
                                val outputStream = PipedOutputStream()
                                val inputStream = PipedInputStream(outputStream)

                                val handler: (byteArray: ByteArray) -> Unit = handler@{ byteArray ->
                                    if (byteArray.isEmpty()) {
                                        httpServer.removeUrl(streamUrl)
                                        return@handler
                                    }
                                    runCatching {
                                        outputStream.write(byteArray)
                                        outputStream.flush()
                                    }.onFailure {
                                        context.log.warn("Failed to write to streaming url ${it.localizedMessage}")
                                    }
                                }

                                override val onOpen: () -> Unit = {
                                    streamHandlers.getOrPut(hashCode) { CopyOnWriteArrayList() }.add(handler)
                                }

                                override val readBytes: (byteArray: ByteArray) -> Int = { byteArray ->
                                    runBlocking {
                                        withTimeoutOrNull(3000L) {
                                            inputStream.read(byteArray)
                                        } ?: -1
                                    }
                                }

                                override val onClose: () -> Unit = {
                                    context.log.verbose("Streaming url closed")
                                    streamHandlers[hashCode]?.remove(handler)
                                    outputStream.close()
                                    inputStream.close()
                                }
                            }
                        }
                    }
                ) ?: return@hook

                context.log.verbose("streaming url = $streamUrl, sampleRate = ${audioFormat.sampleRate}, audioFormat = ${audioFormat.encoding}")

                context.feature(MediaDownloader::class).provideDownloadManagerClient(
                    UUID.randomUUID().toString(),
                    participants.mapNotNull { context.database.getFriendInfo(it)?.mutableUsername }.joinToString("-"),
                    System.currentTimeMillis(),
                    MediaDownloadSource.VOICE_CALL
                ).downloadStream(streamUrl, AudioStreamFormat(audioFormat.channelCount, audioFormat.sampleRate, audioFormat.encoding))
            }

            getMethod("write", ByteBuffer::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).hook(HookStage.BEFORE) { param ->
                streamHandlers[param.thisObject<Any>().hashCode()]?.let { handlers ->
                    val byteBuffer = param.arg<ByteBuffer>(0)
                    val position = byteBuffer.position()
                    val buffer = ByteArray(param.arg(1))
                    byteBuffer.get(buffer)
                    byteBuffer.position(position)
                    handlers.forEach { it(buffer) }
                }
            }

            hook("release", HookStage.BEFORE) {
                streamHandlers.remove(it.thisObject<Any>().hashCode())?.forEach { it(ByteArray(0)) }
            }
        }
    }
}