package me.rhunk.snapenhance.util.export

import android.content.pm.PackageManager
import android.os.Environment
import android.util.Base64InputStream
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.data.MediaReferenceType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.util.getApplicationInfoCompat
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import me.rhunk.snapenhance.util.snap.EncryptionHelper
import me.rhunk.snapenhance.util.snap.MediaDownloaderHelper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.ZipFile
import kotlin.io.encoding.ExperimentalEncodingApi


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
            val messageContent = serializeMessageContent(message) ?: message.messageContent.contentType.name
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(message.messageMetadata.createdAt))
            writer.write("[$date] - $senderDisplayName (${senderUsername}): $messageContent\n")
        }
        writer.flush()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun exportHtml(output: OutputStream) {
        val downloadMediaCacheFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapEnhance/cache").also { it.mkdirs() }
        val mediaFiles = Collections.synchronizedMap(mutableMapOf<String, Pair<FileType, File>>())

        printLog("found ${messages.size} messages")

        withContext(Dispatchers.IO) {
            messages.filter {
                mediaToDownload?.contains(it.messageContent.contentType) ?: false
            }.map { message ->
                async {
                    val remoteMediaReferences by lazy {
                        val serializedMessageContent = context.gson.toJsonTree(message.messageContent.instanceNonNull()).asJsonObject
                        serializedMessageContent["mRemoteMediaReferences"]
                            .asJsonArray.map { it.asJsonObject["mMediaReferences"].asJsonArray }
                            .flatten()
                    }

                    remoteMediaReferences.firstOrNull().takeIf { it != null }?.let { media ->
                        val protoMediaReference = media.asJsonObject["mContentObject"].asJsonArray.map { it.asByte }.toByteArray()

                        runCatching {
                            val downloadedMedia = MediaDownloaderHelper.downloadMediaFromReference(protoMediaReference) {
                                EncryptionHelper.decryptInputStream(it, message.messageContent.contentType, ProtoReader(message.messageContent.content), isArroyo = false)
                            }

                            printLog("downloaded media ${message.orderKey}")

                            downloadedMedia.forEach { (type, mediaData) ->
                                val fileType = FileType.fromByteArray(mediaData)
                                val fileName = "${type}_${kotlin.io.encoding.Base64.UrlSafe.encode(protoMediaReference).replace("=", "")}"

                                val mediaFile = File(downloadMediaCacheFolder, "$fileName.${fileType.fileExtension}")

                                FileOutputStream(mediaFile).use { fos ->
                                    mediaData.inputStream().copyTo(fos)
                                }

                                mediaFiles[fileName] = fileType to mediaFile
                            }
                        }.onFailure {
                            printLog("failed to download media for ${message.messageDescriptor.conversationId}_${message.orderKey}")
                            Logger.error("failed to download media for ${message.messageDescriptor.conversationId}_${message.orderKey}", it)
                        }
                    }
                }
            }.awaitAll()
        }

        printLog("writing downloaded medias...")

        //write the head of the html file
        output.write("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title></title>
            </head>
        """.trimIndent().toByteArray())

        output.write("<!-- This file was generated by SnapEnhance ${BuildConfig.VERSION_NAME} -->\n".toByteArray())

        mediaFiles.forEach { (key, filePair) ->
            printLog("writing $key...")
            output.write("<div class=\"media-$key\"><!-- ".toByteArray())

            val deflateInputStream = DeflaterInputStream(filePair.second.inputStream(), Deflater(Deflater.BEST_COMPRESSION, true))
            val base64InputStream = XposedHelpers.newInstance(
                Base64InputStream::class.java,
                deflateInputStream,
                android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP,
                true
            ) as InputStream
            base64InputStream.copyTo(output)
            deflateInputStream.close()

            output.write(" --></div>\n".toByteArray())
            output.flush()
        }
        printLog("writing json conversation data...")

        //write the json file
        output.write("<script type=\"application/json\" class=\"exported_content\">".toByteArray())
        exportJson(output)
        output.write("</script>\n".toByteArray())

        printLog("writing template...")

        runCatching {
            ZipFile(
                context.androidContext.packageManager.getApplicationInfoCompat(BuildConfig.APPLICATION_ID, PackageManager.GET_META_DATA).publicSourceDir
            ).use { apkFile ->
                //export rawinflate.js
                apkFile.getEntry("assets/web/rawinflate.js").let { entry ->
                    output.write("<script>".toByteArray())
                    apkFile.getInputStream(entry).copyTo(output)
                    output.write("</script>\n".toByteArray())
                }

                //export avenir next font
                apkFile.getEntry("res/font/avenir_next_medium.ttf").let { entry ->
                    val encodedFontData = kotlin.io.encoding.Base64.Default.encode(apkFile.getInputStream(entry).readBytes())
                    output.write("""
                        <style>
                            @font-face {
                                font-family: 'Avenir Next';
                                src: url('data:font/truetype;charset=utf-8;base64, $encodedFontData');
                                font-weight: normal;
                                font-style: normal;
                            }
                        </style>
                    """.trimIndent().toByteArray())
                }

                apkFile.getEntry("assets/web/export_template.html").let { entry ->
                    apkFile.getInputStream(entry).copyTo(output)
                }

                apkFile.close()
            }
        }.onFailure {
            printLog("failed to read template from apk")
            Logger.error("failed to read template from apk", it)
        }

        output.write("</html>".toByteArray())
        output.close()
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
                                messageContentType != ContentType.SNAP &&
                                messageContentType != ContentType.NOTE)
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

        output.write(context.gson.toJson(rootObject).toByteArray())
        output.flush()
    }

    suspend fun exportTo(exportFormat: ExportFormat) {
        val output = FileOutputStream(outputFile)

        when (exportFormat) {
            ExportFormat.HTML -> exportHtml(output)
            ExportFormat.JSON -> exportJson(output)
            ExportFormat.TEXT -> exportText(output)
        }

        output.close()
    }
}