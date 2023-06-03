package me.rhunk.snapenhance.util.export

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MediaReferenceType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Base64


enum class ExportType {
    JSON,
    CSV,
    HTML
}

class MessageExporter(
    private val context: ModContext
) {
    companion object {
        private val prettyPrintGson = GsonBuilder().setPrettyPrinting().create()
    }


    private lateinit var friendFeedInfo: FriendFeedInfo
    private lateinit var conversationParticipants: Map<String, FriendInfo>
    private lateinit var messages: List<Message>
    private lateinit var outputFile: File

    fun readInfo(friendFeedInfo: FriendFeedInfo, messages: List<Message>, outputFile: File) {
        this.outputFile = outputFile
        this.friendFeedInfo = friendFeedInfo

        conversationParticipants =
            context.database.getConversationParticipants(friendFeedInfo.key!!)
                ?.mapNotNull {
                    context.database.getFriendInfo(it)
                }?.associateBy { it.userId!! } ?: emptyMap()

        if (conversationParticipants.isEmpty())
            throw Throwable("Failed to get conversation participants for ${friendFeedInfo.key}")

        this.messages = messages.sortedBy { it.orderKey }
    }

    private fun serializeMessageContent(message: Message): String? {
        return if (message.messageContent.contentType == ContentType.CHAT) {
            ProtoReader(message.messageContent.content).getString(2, 1) ?: "Failed to parse message"
        } else null
    }

    private fun exportJson(output: OutputStream) {
        val rootObject = JsonObject().apply {
            addProperty("conversationId", friendFeedInfo.key)
            addProperty("conversationName", friendFeedInfo.feedDisplayName)
            add("participants", JsonObject().apply {
                conversationParticipants.forEach { (userId, friendInfo) ->
                    add(userId, JsonObject().apply {
                        addProperty("displayName", friendInfo.displayName)
                        addProperty("username", friendInfo.usernameForSorting)
                        addProperty("bitmojiSelfieId", friendInfo.bitmojiSelfieId)
                    })
                }
            })
            add("messages", JsonArray().apply {
                messages.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("orderKey", message.orderKey)
                        addProperty("senderId", message.senderId.toString())
                        addProperty("type", message.messageContent.contentType.toString())
                        addProperty("isSaved", message.messageMetadata.savedBy.isNotEmpty())
                        addProperty("createdTimestamp", message.messageMetadata.createdAt)
                        addProperty("serializedContent", serializeMessageContent(message))
                        addProperty("rawContent", Base64.getEncoder().encodeToString(message.messageContent.content))

                        val messageContentType = message.messageContent.contentType
                        val remoteMediaReferences by lazy {
                            val serializedMessageContent = context.gson.toJsonTree(message.messageContent.instanceNonNull()).asJsonObject
                            serializedMessageContent["mRemoteMediaReferences"]
                                .asJsonArray.map { it.asJsonObject["mMediaReferences"].asJsonArray }
                                .flatten()
                        }

                        add("mediaReferences", JsonArray().apply mediaReferences@ {
                            if (messageContentType != ContentType.EXTERNAL_MEDIA &&
                                messageContentType != ContentType.STICKER &&
                                messageContentType != ContentType.SNAP)
                                return@mediaReferences

                            remoteMediaReferences.forEach { media ->
                                val protoMediaReference = media.asJsonObject["mContentObject"].asJsonArray.map { it.asByte }.toByteArray()
                                val mediaType = MediaReferenceType.valueOf(media.asJsonObject["mMediaType"].asString)
                                add(JsonObject().apply {
                                    addProperty("mediaType", mediaType.toString())
                                    addProperty("content", Base64.getEncoder().encodeToString(protoMediaReference))
                                })
                            }
                        })

                    })
                }
            })
        }

        output.write(prettyPrintGson.toJson(rootObject).toByteArray())
        output.flush()
    }

    fun exportTo(exportType: ExportType) {
        val output = FileOutputStream(outputFile)

        when (exportType) {
            ExportType.JSON -> exportJson(output)
            else -> throw Throwable("Unsupported export type: $exportType")
        }

        output.close()
    }
}