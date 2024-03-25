package me.rhunk.snapenhance.download

import android.media.AudioFormat
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.LogManager
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.config.impl.DownloaderConfig
import me.rhunk.snapenhance.common.data.download.AudioStreamFormat
import me.rhunk.snapenhance.common.logger.LogLevel
import me.rhunk.snapenhance.task.PendingTask
import java.io.File
import java.util.concurrent.Executors


class ArgumentList  {
    private val arguments = mutableListOf<Pair<String, String>>()

    operator fun plusAssign(stringPair: Pair<String, String>) {
        arguments += stringPair
    }

    operator fun plusAssign(key: String) {
        arguments += key to ""
    }

    operator fun minusAssign(key: String) {
        arguments.removeIf { it.first == key }
    }

    operator fun get(key: String) = arguments.find { it.first == key }?.second

    fun forEach(action: (Pair<String, String>) -> Unit) {
        arguments.forEach(action)
    }

    fun clear() {
        arguments.clear()
    }
}


class FFMpegProcessor(
    private val logManager: LogManager,
    private val ffmpegOptions: DownloaderConfig.FFMpegOptions,
    private val onStatistics: (Statistics) -> Unit = {}
) {
    companion object {
        private const val TAG = "ffmpeg-processor"

        fun newFFMpegProcessor(context: RemoteSideContext, pendingTask: PendingTask) = FFMpegProcessor(
            logManager = context.log,
            ffmpegOptions = context.config.root.downloader.ffmpegOptions,
            onStatistics = {
                pendingTask.updateProgress("Processing (frames=${it.videoFrameNumber}, fps=${it.videoFps}, time=${it.time}, bitrate=${it.bitrate}, speed=${it.speed})")
            }
        )
    }
    enum class Action {
        DOWNLOAD_DASH,
        MERGE_OVERLAY,
        CONVERSION,
        MERGE_MEDIA,
        DOWNLOAD_AUDIO_STREAM,
    }

    data class Request(
        val action: Action,
        val inputs: List<String>,
        val output: File,
        val overlay: File? = null, //only for MERGE_OVERLAY
        val startTime: Long? = null, //only for DOWNLOAD_DASH
        val duration: Long? = null, //only for DOWNLOAD_DASH
        val audioStreamFormat: AudioStreamFormat? = null, //only for DOWNLOAD_AUDIO_STREAM

        var videoCodec: String? = null,
        var audioCodec: String? = null,
    )


    private suspend fun newFFMpegTask(globalArguments: ArgumentList, inputArguments: ArgumentList, outputArguments: ArgumentList) = suspendCancellableCoroutine<FFmpegSession> {
        val stringBuilder = StringBuilder()
        arrayOf(globalArguments, inputArguments, outputArguments).forEach { argumentList ->
            argumentList.forEach { (key, value) ->
                stringBuilder.append("$key ${value.takeIf { it.isNotEmpty() }?.plus(" ") ?: ""}")
            }
        }

        logManager.debug("arguments: $stringBuilder", "FFMpegProcessor")

        FFmpegKit.executeAsync(stringBuilder.toString(),
            { session ->
                it.resumeWith(
                    if (session.returnCode.isValueSuccess) {
                        Result.success(session)
                    } else {
                        Result.failure(Exception(session.output))
                    }
                )
            }, logFunction@{ log ->
                logManager.internalLog(TAG, when (log.level) {
                    Level.AV_LOG_ERROR, Level.AV_LOG_FATAL -> LogLevel.ERROR
                    Level.AV_LOG_WARNING -> LogLevel.WARN
                    Level.AV_LOG_VERBOSE -> LogLevel.VERBOSE
                    else -> return@logFunction
                }, log.message)
            }, { onStatistics(it) }, Executors.newSingleThreadExecutor())
    }

    suspend fun execute(args: Request) {
        // load ffmpeg native sync to avoid native crash
        synchronized(this) { FFmpegKit.listSessions() }
        val globalArguments = ArgumentList().apply {
            this += "-y"
            this += "-threads" to ffmpegOptions.threads.get().toString()
        }

        val inputArguments = ArgumentList().apply {
            args.inputs.forEach { path ->
                this += "-i" to path
            }
        }

        val outputArguments = ArgumentList().apply {
            this += "-preset" to (ffmpegOptions.preset.getNullable() ?: "ultrafast")
            this += "-c:v" to (ffmpegOptions.customVideoCodec.get().takeIf { it.isNotEmpty() } ?: "libx264")
            this += "-c:a" to (ffmpegOptions.customAudioCodec.get().takeIf { it.isNotEmpty() } ?: "copy")
            this += "-crf" to ffmpegOptions.constantRateFactor.get().let { "\"$it\"" }
            this += "-b:v" to ffmpegOptions.videoBitrate.get().toString() + "K"
            this += "-b:a" to ffmpegOptions.audioBitrate.get().toString() + "K"
        }

        when (args.action) {
            Action.DOWNLOAD_DASH -> {
                outputArguments += "-ss" to "'${args.startTime}ms'"
                if (args.duration != null) {
                    outputArguments += "-t" to "'${args.duration}ms'"
                }
            }
            Action.MERGE_OVERLAY -> {
                inputArguments += "-i" to args.overlay!!.absolutePath
                outputArguments += "-filter_complex" to "\"[0]scale2ref[img][vid];[img]setsar=1[img];[vid]nullsink;[img][1]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\""
            }
            Action.CONVERSION -> {
                if (ffmpegOptions.customAudioCodec.isEmpty()) {
                    outputArguments -= "-c:a"
                }
                outputArguments -= "-c:v"
                args.videoCodec?.let {
                    outputArguments += "-c:v" to it
                } ?: run {
                    outputArguments += "-vn"
                }
                args.audioCodec?.let {
                    outputArguments -= "-c:a"
                    outputArguments += "-c:a" to it
                }
            }
            Action.MERGE_MEDIA -> {
                inputArguments.clear()
                val filesInfo = args.inputs.mapNotNull { file ->
                    runCatching {
                        MediaMetadataRetriever().apply { setDataSource(file) }
                    }.getOrNull()?.let { file to it }
                }

                val (maxWidth, maxHeight) = filesInfo.maxByOrNull { (_, r) ->
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                }?.let { (_, r) ->
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() to
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                } ?: throw Exception("Failed to get video size")

                val filterFirstPart = StringBuilder()
                val filterSecondPart = StringBuilder()
                var containsNoSound = false

                filesInfo.forEachIndexed { index, (file, retriever) ->
                    filterFirstPart.append("[$index:v]scale=$maxWidth:$maxHeight,setsar=1[v$index];")
                    if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") {
                        filterSecondPart.append("[v$index][$index:a]")
                    } else {
                        containsNoSound = true
                        filterSecondPart.append("[v$index][${filesInfo.size}]")
                    }
                    inputArguments += "-i" to file
                }

                if (containsNoSound) {
                    inputArguments += "-f" to "lavfi"
                    inputArguments += "-t" to "0.1"
                    inputArguments += "-i" to "anullsrc=channel_layout=stereo:sample_rate=44100"
                }

                if (outputArguments["-c:a"] == "copy") {
                    outputArguments -= "-c:a"
                }

                outputArguments += "-fps_mode" to "vfr"

                outputArguments += "-filter_complex" to "\"$filterFirstPart ${filterSecondPart}concat=n=${args.inputs.size}:v=1:a=1[vout][aout]\""
                outputArguments += "-map" to "\"[aout]\""
                outputArguments += "-map" to "\"[vout]\""

                filesInfo.forEach { it.second.close() }
            }
            Action.DOWNLOAD_AUDIO_STREAM -> {
                outputArguments.clear()
                globalArguments += "-f" to when (args.audioStreamFormat!!.encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> "u8"
                    AudioFormat.ENCODING_PCM_16BIT -> "s16le"
                    AudioFormat.ENCODING_PCM_FLOAT -> "f32le"
                    AudioFormat.ENCODING_PCM_32BIT -> "s32le"
                    else -> throw IllegalArgumentException("Unsupported audio encoding")
                }
                globalArguments += "-ar" to args.audioStreamFormat!!.sampleRate.toString()
                globalArguments += "-ac" to args.audioStreamFormat!!.channels.toString()
            }
        }
        outputArguments += args.output.absolutePath
        newFFMpegTask(globalArguments, inputArguments, outputArguments)
    }
}