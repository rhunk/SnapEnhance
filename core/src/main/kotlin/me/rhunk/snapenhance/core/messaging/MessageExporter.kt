package me.rhunk.snapenhance.core.messaging

import android.os.Environment
import android.util.Base64InputStream
import android.util.Base64OutputStream
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.BuildConfig
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.FileType
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.common.util.snap.RemoteMediaResolver
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.AttachmentType
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipFile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


enum class ExportFormat(
    val extension: String,
){
    JSON("json"),
    TEXT("txt"),
    HTML("html");
}

@OptIn(ExperimentalEncodingApi::class)
class MessageExporter(
    private val context: ModContext,
    private val outputFile: File,
    private val friendFeedEntry: FriendFeedEntry,
    private val mediaToDownload: List<ContentType>? = null,
    private val printLog: (String) -> Unit = {},
) {
    private lateinit var conversationParticipants: Map<String, FriendInfo>
    private lateinit var messages: List<Message>

    fun readMessages(messages: List<Message>) {
        conversationParticipants =
            context.database.getConversationParticipants(friendFeedEntry.key!!)
                ?.mapNotNull {
                    context.database.getFriendInfo(it)
                }?.associateBy { it.userId!! } ?: emptyMap()

        if (conversationParticipants.isEmpty())
            throw Throwable("Failed to get conversation participants for ${friendFeedEntry.key}")

        this.messages = messages.sortedBy { it.orderKey }
    }

    private fun serializeMessageContent(message: Message): String? {
        return if (message.messageContent!!.contentType == ContentType.CHAT) {
            ProtoReader(message.messageContent!!.content!!).getString(2, 1) ?: "Failed to parse message"
        } else null
    }

    private fun exportText(output: OutputStream) {
        val writer = output.bufferedWriter()
        writer.write("Conversation key: ${friendFeedEntry.key}\n")
        writer.write("Conversation Name: ${friendFeedEntry.feedDisplayName}\n")
        writer.write("Participants:\n")
        conversationParticipants.forEach { (userId, friendInfo) ->
            writer.write("  $userId: ${friendInfo.displayName}\n")
        }

        writer.write("\nMessages:\n")
        messages.forEach { message ->
            val sender = conversationParticipants[message.senderId.toString()]
            val senderUsername = sender?.usernameForSorting ?: message.senderId.toString()
            val senderDisplayName = sender?.displayName ?: message.senderId.toString()
            val messageContent = serializeMessageContent(message) ?: message.messageContent!!.contentType?.name
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(message.messageMetadata!!.createdAt!!))
            writer.write("[$date] - $senderDisplayName (${senderUsername}): $messageContent\n")
        }
        writer.flush()
    }

    private suspend fun exportHtml(output: OutputStream) {
        val downloadMediaCacheFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapEnhance/cache").also { it.mkdirs() }
        val mediaFiles = Collections.synchronizedMap(mutableMapOf<String, Pair<FileType, File>>())
        val threadPool = Executors.newFixedThreadPool(15)

        withContext(Dispatchers.IO) {
            var processCount = 0

            fun updateProgress(type: String) {
                val total = messages.filter {
                    mediaToDownload?.contains(it.messageContent!!.contentType) ?: false
                }.size
                processCount++
                printLog("$type $processCount/$total")
            }

            messages.filter {
                mediaToDownload?.contains(it.messageContent!!.contentType) ?: false
            }.forEach { message ->
                threadPool.execute {
                    MessageDecoder.decode(message.messageContent!!).forEach decode@{ attachment ->
                        val protoMediaReference = Base64.UrlSafe.decode(attachment.mediaUrlKey ?: return@decode)

                        runCatching {
                            RemoteMediaResolver.downloadBoltMedia(protoMediaReference, decryptionCallback = {
                                (attachment.attachmentInfo?.encryption?.decryptInputStream(it) ?: it)
                            }) { downloadedInputStream, _ ->
                                downloadedInputStream.use { inputStream ->
                                    MediaDownloaderHelper.getSplitElements(inputStream) { type, splitInputStream ->
                                        val fileName = "${type}_${Base64.UrlSafe.encode(protoMediaReference).replace("=", "")}"
                                        val bufferedInputStream = BufferedInputStream(splitInputStream)
                                        val fileType = MediaDownloaderHelper.getFileType(bufferedInputStream)
                                        val mediaFile = File(downloadMediaCacheFolder, "$fileName.${fileType.fileExtension}")

                                        FileOutputStream(mediaFile).use { fos ->
                                            bufferedInputStream.copyTo(fos)
                                        }

                                        mediaFiles[fileName] = fileType to mediaFile
                                    }
                                }
                            }

                            updateProgress("downloaded")
                        }.onFailure {
                            printLog("failed to download media for ${message.messageDescriptor!!.conversationId}_${message.orderKey}")
                            context.log.error("failed to download media for ${message.messageDescriptor!!.conversationId}_${message.orderKey}", it)
                        }
                    }
                }
            }

            threadPool.shutdown()
            threadPool.awaitTermination(30, TimeUnit.DAYS)
            processCount = 0

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
                output.write("<div class=\"media-$key\"><!-- ".toByteArray())
                filePair.second.inputStream().use { inputStream ->
                    val deflateInputStream = DeflaterInputStream(inputStream, Deflater(Deflater.BEST_COMPRESSION, true))
                    (XposedHelpers.newInstance(
                        Base64InputStream::class.java,
                        deflateInputStream,
                        android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP,
                        true
                    ) as InputStream).copyTo(output)
                }
                output.write(" --></div>\n".toByteArray())
                output.flush()
                updateProgress("wrote")
            }
            printLog("writing json conversation data...")

            //write the json file
            output.write("<script type=\"application/json\" class=\"exported_content\">".toByteArray())

            val b64os = (XposedHelpers.newInstance(
                Base64OutputStream::class.java,
                output,
                android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP,
                true
            ) as OutputStream)
            val deflateOutputStream = DeflaterOutputStream(b64os, Deflater(Deflater.BEST_COMPRESSION, true), true)
            exportJson(deflateOutputStream)
            deflateOutputStream.finish()
            b64os.flush()

            output.write("</script>\n".toByteArray())

            printLog("writing template...")

            runCatching {
                ZipFile(context.bridgeClient.getApplicationApkPath()).use { apkFile ->
                    //export rawinflate.js
                    apkFile.getEntry("assets/web/rawinflate.js")?.let { entry ->
                        output.write("<script>".toByteArray())
                        apkFile.getInputStream(entry).copyTo(output)
                        output.write("</script>\n".toByteArray())
                    }

                    //export avenir next font
                    apkFile.getEntry("assets/web/avenir_next_medium.ttf")?.let { entry ->
                        val encodedFontData = Base64.Default.encode(apkFile.getInputStream(entry).readBytes())
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

                    apkFile.getEntry("assets/web/export_template.html")?.let { entry ->
                        apkFile.getInputStream(entry).copyTo(output)
                    }

                    apkFile.close()
                }
            }.onFailure {
                throw Throwable("Failed to read template from apk", it)
            }

            output.write("</html>".toByteArray())
            output.close()
        }
    }

    private fun exportJson(output: OutputStream) {
        val rootObject = JsonObject().apply {
            addProperty("conversationId", friendFeedEntry.key)
            addProperty("conversationName", friendFeedEntry.feedDisplayName)

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
                        addProperty("type", message.messageContent!!.contentType.toString())

                        fun addUUIDList(name: String, list: List<SnapUUID>) {
                            add(name, JsonArray().apply {
                                list.map { participants.getOrDefault(it.toString(), -1) }.forEach { add(it) }
                            })
                        }

                        addUUIDList("savedBy", message.messageMetadata!!.savedBy!!)
                        addUUIDList("seenBy", message.messageMetadata!!.seenBy!!)
                        addUUIDList("openedBy", message.messageMetadata!!.openedBy!!)

                        add("reactions", JsonObject().apply {
                            message.messageMetadata!!.reactions!!.forEach { reaction ->
                                addProperty(
                                    participants.getOrDefault(reaction.userId.toString(), -1L).toString(),
                                    reaction.reactionId
                                )
                            }
                        })

                        addProperty("createdTimestamp", message.messageMetadata!!.createdAt)
                        addProperty("readTimestamp", message.messageMetadata!!.readAt)
                        addProperty("serializedContent", serializeMessageContent(message))
                        addProperty("rawContent", Base64.UrlSafe.encode(message.messageContent!!.content!!))

                        add("attachments", JsonArray().apply {
                            MessageDecoder.decode(message.messageContent!!)
                                .forEach attachments@{ attachments ->
                                if (attachments.type == AttachmentType.STICKER) //TODO: implement stickers
                                    return@attachments
                                add(JsonObject().apply {
                                    addProperty("key", attachments.mediaUrlKey?.replace("=", ""))
                                    addProperty("type", attachments.type.toString())
                                    add("encryption", attachments.attachmentInfo?.encryption?.let { encryption ->
                                        JsonObject().apply {
                                            addProperty("key", encryption.key)
                                            addProperty("iv", encryption.iv)
                                        }
                                    } ?: JsonNull.INSTANCE)
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
        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).apply {
                when (exportFormat) {
                    ExportFormat.HTML -> exportHtml(this)
                    ExportFormat.JSON -> exportJson(this)
                    ExportFormat.TEXT -> exportText(this)
                }
                close()
            }
        }
    }
}