package me.rhunk.snapenhance.util.export

import android.os.Environment
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.data.MediaReferenceType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import me.rhunk.snapenhance.util.snap.EncryptionHelper
import me.rhunk.snapenhance.util.snap.MediaDownloaderHelper
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


enum class ExportFormat(
    val extension: String,
){
    JSON("json"),
    TEXT("txt"),
    HTML("html");
}

class MessageExporter(
    private val context: ModContext,
    private val outputFile: File,
    private val friendFeedInfo: FriendFeedInfo,
    private val mediaToDownload: List<ContentType>? = null,
    private val printLog: (String) -> Unit = {},
) {
    companion object {
        private val prettyPrintGson = GsonBuilder().setPrettyPrinting().create()
    }

    private lateinit var conversationParticipants: Map<String, FriendInfo>
    private lateinit var messages: List<Message>

    fun readMessages(messages: List<Message>) {
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

    private fun exportText(output: OutputStream) {
        val writer = output.bufferedWriter()
        writer.write("Conversation key: ${friendFeedInfo.key}\n")
        writer.write("Conversation Name: ${friendFeedInfo.feedDisplayName}\n")
        writer.write("Participants:\n")
        conversationParticipants.forEach { (userId, friendInfo) ->
            writer.write("  $userId: ${friendInfo.displayName}\n")
        }

        writer.write("\nMessages:\n")
        messages.forEach { message ->
            val sender = conversationParticipants[message.senderId.toString()]
            val senderUsername = sender?.usernameForSorting ?: message.senderId.toString()
            val senderDisplayName = sender?.displayName ?: message.senderId.toString()
            val messageContent = serializeMessageContent(message) ?: "Failed to parse message"
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(message.messageMetadata.createdAt))
            writer.write("[$date] - $senderDisplayName (${senderUsername}): $messageContent\n")
        }
        writer.flush()
    }

    private fun exportHtml(output: OutputStream) {
        //first download all medias and put it into a file
        val downloadMediaCacheFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapEnhance/cache").also { it.mkdirs() }
        val mediaFiles = Collections.synchronizedMap(mutableMapOf<String, Pair<FileType, File>>())

        printLog("found ${messages.size} messages")

        runBlocking {
            messages.filter {
                mediaToDownload?.contains(it.messageContent.contentType) ?: false
            }.forEach { message ->
                val remoteMediaReferences by lazy {
                    val serializedMessageContent = context.gson.toJsonTree(message.messageContent.instanceNonNull()).asJsonObject
                    serializedMessageContent["mRemoteMediaReferences"]
                        .asJsonArray.map { it.asJsonObject["mMediaReferences"].asJsonArray }
                        .flatten()
                }

                remoteMediaReferences.firstOrNull().takeIf { it != null }?.let { media ->
                    val protoMediaReference = media.asJsonObject["mContentObject"].asJsonArray.map { it.asByte }.toByteArray()

                    launch(Dispatchers.IO) {
                        val downloadedMedia = MediaDownloaderHelper.downloadMediaFromReference(protoMediaReference) {
                            EncryptionHelper.decryptInputStream(it, message.messageContent.contentType, ProtoReader(message.messageContent.content), isArroyo = false)
                        }

                        downloadedMedia.forEach { (type, mediaData) ->
                            val fileType = FileType.fromByteArray(mediaData)
                            val fileName = "${message.messageDescriptor.conversationId}_${message.orderKey}_$type"

                            val mediaFile = File(downloadMediaCacheFolder, "$fileName.${fileType.fileExtension}")
                            FileOutputStream(mediaFile).use { fos ->
                                fos.write(mediaData)
                            }

                            mediaFiles[fileName] = fileType to mediaFile
                            Logger.debug("exported $fileName")
                        }
                    }
                }
            }
        }

        printLog("downloaded ${mediaFiles.size} medias")
        printLog("packing medias into a zip file")
        //create a zip file with all medias
        val zipFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            mediaFiles.forEach { (fileKey, mediaInfoPair) ->
                printLog("adding $fileKey to zip file")
                zos.putNextEntry(ZipEntry("assets/${fileKey}.${mediaInfoPair.first.fileExtension}"))
                mediaInfoPair.second.inputStream().use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }

        printLog("done")
    }

    private fun exportJson(output: OutputStream) {
        val rootObject = JsonObject().apply {
            addProperty("conversationId", friendFeedInfo.key)
            addProperty("conversationName", friendFeedInfo.feedDisplayName)

            var index = 0
            val participants = mutableMapOf<String, Int>()

            add("participants", JsonObject().apply {
                conversationParticipants.forEach { (userId, friendInfo) ->
                    add(userId, JsonObject().apply {
                        addProperty("id", index)
                        addProperty("displayName", friendInfo.displayName)
                        addProperty("username", friendInfo.usernameForSorting)
                        addProperty("bitmojiSelfieId", friendInfo.bitmojiSelfieId)
                    })
                    participants[userId] = index++
                }
            })
            add("messages", JsonArray().apply {
                messages.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("orderKey", message.orderKey)
                        addProperty("senderId", participants.getOrDefault(message.senderId.toString(), -1))
                        addProperty("type", message.messageContent.contentType.toString())

                        fun addUUIDList(name: String, list: List<SnapUUID>) {
                            add(name, JsonArray().apply {
                                list.map { participants.getOrDefault(it.toString(), -1) }.forEach { add(it) }
                            })
                        }

                        addUUIDList("savedBy", message.messageMetadata.savedBy)
                        addUUIDList("seenBy", message.messageMetadata.seenBy)
                        addUUIDList("openedBy", message.messageMetadata.openedBy)

                        add("reactions", JsonObject().apply {
                            message.messageMetadata.reactions.forEach { reaction ->
                                addProperty(
                                    participants.getOrDefault(reaction.userId.toString(), -1L).toString(),
                                    reaction.reactionId
                                )
                            }
                        })

                        addProperty("createdTimestamp", message.messageMetadata.createdAt)
                        addProperty("readTimestamp", message.messageMetadata.readAt)
                        addProperty("serializedContent", serializeMessageContent(message))
                        addProperty("rawContent", Base64.getUrlEncoder().encodeToString(message.messageContent.content))

                        val messageContentType = message.messageContent.contentType

                        EncryptionHelper.getEncryptionKeys(messageContentType, ProtoReader(message.messageContent.content), isArroyo = false)?.let { encryptionKeyPair ->
                            add("encryption", JsonObject().apply encryption@{
                                addProperty("key", Base64.getEncoder().encodeToString(encryptionKeyPair.first))
                                addProperty("iv", Base64.getEncoder().encodeToString(encryptionKeyPair.second))
                            })
                        }

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
                                    addProperty("content", Base64.getUrlEncoder().encodeToString(protoMediaReference))
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

    fun exportTo(exportFormat: ExportFormat) {
        val output = FileOutputStream(outputFile)

        when (exportFormat) {
            ExportFormat.HTML -> exportHtml(output)
            ExportFormat.JSON -> exportJson(output)
            ExportFormat.TEXT -> exportText(output)
        }

        output.close()
    }
}