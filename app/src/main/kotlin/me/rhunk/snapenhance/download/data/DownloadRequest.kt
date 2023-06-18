package me.rhunk.snapenhance.download.data

import android.os.Bundle
import me.rhunk.snapenhance.download.enums.DownloadMediaType


data class DashOptions(val offsetTime: Long, val duration: Long?)
data class InputMedia(
    val content: String,
    val type: DownloadMediaType,
    val encryption: MediaEncryptionKeyPair? = null
)

class DownloadRequest(
    private val outputPath: String = "",
    private val inputMedias: Array<String>,
    private val inputTypes: Array<String>,
    private val mediaEncryption: Map<String, MediaEncryptionKeyPair> = emptyMap(),
    private val flags: Int = 0,
    private val dashOptions: Map<String, String?>? = null,
    private val mediaDisplaySource: String? = null,
    private val mediaDisplayType: String? = null
) {
    companion object {
        fun fromBundle(bundle: Bundle): DownloadRequest {
            return DownloadRequest(
                outputPath = bundle.getString("outputPath")!!,
                mediaDisplaySource = bundle.getString("mediaDisplaySource"),
                mediaDisplayType = bundle.getString("mediaDisplayType"),
                inputMedias = bundle.getStringArray("inputMedias")!!,
                inputTypes = bundle.getStringArray("inputTypes")!!,
                mediaEncryption = bundle.getStringArray("mediaEncryption")?.associate { entry ->
                    entry.split("|").let {
                        it[0] to MediaEncryptionKeyPair(it[1], it[2])
                    }
                } ?: emptyMap(),
                dashOptions = bundle.getBundle("dashOptions")?.let { options ->
                    options.keySet().associateWith { key ->
                        options.getString(key)
                    }
                },
                flags = bundle.getInt("flags", 0)
            )
        }
    }

    fun toBundle(): Bundle {
        return Bundle().apply {
            putString("outputPath", outputPath)
            putString("mediaDisplaySource", mediaDisplaySource)
            putString("mediaDisplayType", mediaDisplayType)
            putStringArray("inputMedias", inputMedias)
            putStringArray("inputTypes", inputTypes)
            putStringArray("mediaEncryption", mediaEncryption.map { entry ->
                "${entry.key}|${entry.value.key}|${entry.value.iv}"
            }.toTypedArray())
            putBundle("dashOptions", dashOptions?.let { bundle ->
                Bundle().apply {
                    bundle.forEach { (key, value) ->
                        putString(key, value)
                    }
                }
            })
            putInt("flags", flags)
        }
    }

    object Flags {
        const val SHOULD_MERGE_OVERLAY = 1
        const val IS_DASH_PLAYLIST = 2
    }

    val isDashPlaylist: Boolean
        get() = flags and Flags.IS_DASH_PLAYLIST != 0

    val shouldMergeOverlay: Boolean
        get() = flags and Flags.SHOULD_MERGE_OVERLAY != 0

    fun getDashOptions(): DashOptions? {
        return dashOptions?.let {
            DashOptions(
                offsetTime = it["offsetTime"]?.toLong() ?: 0,
                duration = it["duration"]?.toLong()
            )
        }
    }

    fun getInputMedia(index: Int): String? {
        return inputMedias.getOrNull(index)
    }

    fun getInputMedias(): List<InputMedia> {
        return inputMedias.mapIndexed { index, uri ->
            InputMedia(
                content = uri,
                type = DownloadMediaType.valueOf(inputTypes[index]),
                encryption = mediaEncryption.getOrDefault(uri, null)
            )
        }
    }

    fun getInputType(index: Int): DownloadMediaType? {
        return inputTypes.getOrNull(index)?.let { DownloadMediaType.valueOf(it) }
    }
}