package me.rhunk.snapenhance.core.features.impl.downloader.decoder

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.rhunk.snapenhance.common.data.download.toKeyPair
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.wrapper.impl.MessageContent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class DecodedAttachment(
    val mediaUrlKey: String?,
    val type: AttachmentType,
    val attachmentInfo: AttachmentInfo?
) {
    @OptIn(ExperimentalEncodingApi::class)
    val mediaUniqueId: String? by lazy {
        runCatching { Base64.UrlSafe.decode(mediaUrlKey.toString()) }.getOrNull()?.let { ProtoReader(it).getString(2, 2)?.substringBefore(".") }
    }
}

@OptIn(ExperimentalEncodingApi::class)
object MessageDecoder {
    private val gson = GsonBuilder().create()

    private fun decodeAttachment(protoReader: ProtoReader): AttachmentInfo? {
        val mediaInfo = protoReader.followPath(1, 1) ?: return null

        return AttachmentInfo(
            encryption = run {
                val encryptionProtoIndex = if (mediaInfo.contains(19)) 19 else 4
                val encryptionProto = mediaInfo.followPath(encryptionProtoIndex) ?: return@run null

                var key = encryptionProto.getByteArray(1) ?: return@run null
                var iv = encryptionProto.getByteArray(2) ?: return@run null

                if (encryptionProtoIndex == 4) {
                    key = Base64.decode(encryptionProto.getString(1)?.replace("\n","") ?: return@run null)
                    iv = Base64.decode(encryptionProto.getString(2)?.replace("\n","") ?: return@run null)
                }

                Pair(key, iv).toKeyPair()
            },
            resolution = mediaInfo.followPath(5)?.let {
                (it.getVarInt(1)?.toInt() ?: 0) to (it.getVarInt(2)?.toInt() ?: 0)
            },
            duration = mediaInfo.getVarInt(15)   // external medias
                ?: mediaInfo.getVarInt(13) // audio notes
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun getEncodedMediaReferences(messageContent: JsonElement): List<String> {
        return getMediaReferences(messageContent).map { reference ->
                Base64.UrlSafe.encode(
                    reference.asJsonObject.getAsJsonArray("mContentObject").map { it.asByte }.toByteArray()
                )
            }
            .toList()
    }

    fun getEncodedMediaReferences(messageContent: MessageContent): List<String> {
        return getEncodedMediaReferences(gson.toJsonTree(messageContent.instanceNonNull()))
    }

    fun getMediaReferences(messageContent: JsonElement): List<JsonElement> {
        return messageContent.asJsonObject.getAsJsonArray("mRemoteMediaReferences")
            .asSequence()
            .map { it.asJsonObject.getAsJsonArray("mMediaReferences") }
            .flatten()
            .sortedBy {
                it.asJsonObject["mMediaListId"].asLong
            }.toList()
    }


    fun decode(messageContent: MessageContent): List<DecodedAttachment> {
        return decode(
            ProtoReader(messageContent.content!!),
            customMediaReferences = getEncodedMediaReferences(gson.toJsonTree(messageContent.instanceNonNull()))
        )
    }

    fun decode(messageContent: JsonObject): List<DecodedAttachment> {
        return decode(
            ProtoReader(messageContent.getAsJsonArray("mContent")
                .map { it.asByte }
                .toByteArray()),
            customMediaReferences = getEncodedMediaReferences(messageContent)
        )
    }

    fun decode(
        protoReader: ProtoReader,
        customMediaReferences: List<String>? = null // when customReferences is null it means that the message is from arroyo database
    ): List<DecodedAttachment> {
        val decodedAttachment = mutableListOf<DecodedAttachment>()
        val mediaReferences = mutableListOf<String>()
        customMediaReferences?.let { mediaReferences.addAll(it) }
        var mediaKeyIndex = 0

        fun decodeMedia(type: AttachmentType, protoReader: ProtoReader) {
            decodedAttachment.add(
                DecodedAttachment(
                    mediaUrlKey = mediaReferences.getOrNull(mediaKeyIndex++),
                    type = type,
                    attachmentInfo = decodeAttachment(protoReader) ?: return
                )
            )
        }

        // for snaps, external media, and original story replies
        fun decodeDirectMedia(type: AttachmentType, protoReader: ProtoReader) {
            protoReader.followPath(5) { decodeMedia(type,this) }
        }

        fun decodeSticker(protoReader: ProtoReader) {
            protoReader.followPath(1) {
                decodedAttachment.add(
                    DecodedAttachment(
                        mediaUrlKey = null,
                        type = AttachmentType.STICKER,
                        attachmentInfo = BitmojiSticker(
                            reference = getString(2) ?: return@followPath
                        )
                    )
                )
            }
        }

        // media keys
        protoReader.eachBuffer(4, 5) {
            getByteArray(1, 3)?.also { mediaKey ->
                mediaReferences.add(Base64.UrlSafe.encode(mediaKey))
            }
        }

        val mediaReader = customMediaReferences?.let { protoReader } ?: protoReader.followPath(4, 4) ?: return emptyList()

        mediaReader.apply {
            // external media
            eachBuffer(3, 3) {
                decodeDirectMedia(AttachmentType.EXTERNAL_MEDIA, this)
            }

            // stickers
            followPath(4) { decodeSticker(this) }

            // shares
            followPath(5, 24, 2) {
                decodeDirectMedia(AttachmentType.EXTERNAL_MEDIA, this)
            }

            // audio notes
            followPath(6) note@{
                val audioNote = decodeAttachment(this) ?: return@note

                decodedAttachment.add(
                    DecodedAttachment(
                        mediaUrlKey = mediaReferences.getOrNull(mediaKeyIndex++),
                        type = AttachmentType.NOTE,
                        attachmentInfo = audioNote
                    )
                )
            }

            // story replies
            followPath(7) {
                // original story reply
                followPath(3) {
                    decodeDirectMedia(AttachmentType.ORIGINAL_STORY, this)
                }

                // external medias
                followPath(12) {
                    eachBuffer(3) { decodeDirectMedia(AttachmentType.EXTERNAL_MEDIA, this) }
                }

                // attached sticker
                followPath(13) { decodeSticker(this) }

                // attached audio note
                followPath(15) { decodeMedia(AttachmentType.NOTE, this) }
            }

            // snaps
            followPath(11) {
                decodeDirectMedia(AttachmentType.SNAP, this)
            }
        }

        return decodedAttachment
    }
}