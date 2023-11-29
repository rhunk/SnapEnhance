package me.rhunk.snapenhance.download

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.LogManager
import me.rhunk.snapenhance.common.config.impl.DownloaderConfig
import me.rhunk.snapenhance.common.logger.LogLevel
import java.io.File
import java.util.concurrent.Executors


class ArgumentList : LinkedHashMap<String, MutableList<String>>() {
    operator fun plusAssign(stringPair: Pair<String, String>) {
        val (key, value) = stringPair
        if (this.containsKey(key)) {
            this[key]!!.add(value)
        } else {
            this[key] = mutableListOf(value)
        }
    }

    operator fun plusAssign(key: String) {
        this[key] = mutableListOf<String>().apply {
            this += ""
        }
    }

    operator fun minusAssign(key: String) {
        this.remove(key)
    }
}


class FFMpegProcessor(
    private val logManager: LogManager,
    private val ffmpegOptions: DownloaderConfig.FFMpegOptions,
    private val onStatistics: (Statistics) -> Unit = {}
) {
    companion object {
        private const val TAG = "ffmpeg-processor"
    }
    enum class Action {
        DOWNLOAD_DASH,
        MERGE_OVERLAY,
        AUDIO_CONVERSION,
    }

    data class Request(
        val action: Action,
        val input: File,
        val output: File,
        val overlay: File? = null, //only for MERGE_OVERLAY
        val startTime: Long? = null, //only for DOWNLOAD_DASH
        val duration: Long? = null //only for DOWNLOAD_DASH
    )


    private suspend fun newFFMpegTask(globalArguments: ArgumentList, inputArguments: ArgumentList, outputArguments: ArgumentList) = suspendCancellableCoroutine<FFmpegSession> {
        val stringBuilder = StringBuilder()
        arrayOf(globalArguments, inputArguments, outputArguments).forEach { argumentList ->
            argumentList.forEach { (key, values) ->
                values.forEach valueForEach@{ value ->
                    if (value.isEmpty()) {
                        stringBuilder.append("$key ")
                        return@valueForEach
                    }
                    stringBuilder.append("$key $value ")
                }
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
            this += "-i" to args.input.absolutePath
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
            Action.AUDIO_CONVERSION -> {
                if (ffmpegOptions.customAudioCodec.isEmpty()) {
                    outputArguments -= "-c:a"
                }
                if (ffmpegOptions.customVideoCodec.isEmpty()) {
                    outputArguments -= "-c:v"
                }
            }
        }
        outputArguments += args.output.absolutePath
        newFFMpegTask(globalArguments, inputArguments, outputArguments)
    }
}