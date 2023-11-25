package me.rhunk.snapenhance.core.action.impl

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Environment
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.*
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.messaging.ConversationExporter
import me.rhunk.snapenhance.core.messaging.ExportFormat
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.wrapper.impl.Message
import java.io.File
import kotlin.math.absoluteValue

class ExportChatMessages : AbstractAction() {
    private val dialogLogs = mutableListOf<String>()
    private var currentActionDialog: AlertDialog? = null

    private var exportType: ExportFormat? = null
    private var mediaToDownload: List<ContentType>? = null
    private var amountOfMessages: Int? = null

    private fun logDialog(message: String) {
        context.runOnUiThread {
            if (dialogLogs.size > 10) dialogLogs.removeAt(0)
            dialogLogs.add(message)
            context.log.debug("dialog: $message", "ExportChatMessages")
            currentActionDialog!!.setMessage(dialogLogs.joinToString("\n"))
        }
    }

    private fun setStatus(message: String) {
        context.runOnUiThread {
            currentActionDialog!!.setTitle(message)
        }
    }

    private suspend fun askExportType() = suspendCancellableCoroutine { cont ->
        context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["chat_export.select_export_format"])
                .setItems(ExportFormat.entries.map { it.name }.toTypedArray()) { _, which ->
                    cont.resumeWith(Result.success(ExportFormat.entries[which]))
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .show()
        }
    }

    private suspend fun askAmountOfMessages() = suspendCancellableCoroutine { cont ->
        context.coroutineScope.launch(Dispatchers.Main) {
            val input = EditText(context.mainActivity)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setSingleLine()
            input.maxLines = 1

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["chat_export.select_amount_of_messages"])
                .setView(input)
                .setPositiveButton(context.translation["button.ok"]) { _, _ ->
                    cont.resumeWith(Result.success(input.text.takeIf { it.isNotEmpty() }?.toString()?.toIntOrNull()?.absoluteValue))
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .show()
        }
    }

    private suspend fun askMediaToDownload() = suspendCancellableCoroutine { cont ->
        context.runOnUiThread {
            val mediasToDownload = mutableListOf<ContentType>()
            val contentTypes = arrayOf(
                ContentType.CHAT,
                ContentType.SNAP,
                ContentType.EXTERNAL_MEDIA,
                ContentType.NOTE,
                ContentType.STICKER
            )
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["chat_export.select_media_type"])
                .setMultiChoiceItems(contentTypes.map { it.name }.toTypedArray(), BooleanArray(contentTypes.size) { false }) { _, which, isChecked ->
                    val media = contentTypes[which]
                    if (isChecked) {
                        mediasToDownload.add(media)
                    } else if (mediasToDownload.contains(media)) {
                        mediasToDownload.remove(media)
                    }
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .setPositiveButton(context.translation["button.ok"]) { _, _ ->
                    cont.resumeWith(Result.success(mediasToDownload))
                }
                .show()
        }
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            exportType = askExportType() ?: return@launch
            mediaToDownload = if (exportType == ExportFormat.HTML) askMediaToDownload() else null
            amountOfMessages = askAmountOfMessages()

            val friendFeedEntries = context.database.getFeedEntries(500)
            val selectedConversations = mutableListOf<FriendFeedEntry>()

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["chat_export.select_conversation"])
                .setMultiChoiceItems(
                    friendFeedEntries.map { it.feedDisplayName ?: it.friendDisplayUsername!!.split("|").firstOrNull() }.toTypedArray(),
                    BooleanArray(friendFeedEntries.size) { false }
                ) { _, which, isChecked ->
                    if (isChecked) {
                        selectedConversations.add(friendFeedEntries[which])
                    } else if (selectedConversations.contains(friendFeedEntries[which])) {
                        selectedConversations.remove(friendFeedEntries[which])
                    }
                }
                .setNegativeButton(context.translation["chat_export.dialog_negative_button"]) { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton(context.translation["chat_export.dialog_neutral_button"]) { _, _ ->
                    exportChatForConversations(friendFeedEntries)
                }
                .setPositiveButton(context.translation["chat_export.dialog_positive_button"]) { _, _ ->
                    exportChatForConversations(selectedConversations)
                }
                .show()
        }
    }

    private suspend fun fetchMessagesPaginated(conversationId: String, lastMessageId: Long, amount: Int): List<Message> = runBlocking {
        for (i in 0..5) {
            val messages: List<Message>? = suspendCancellableCoroutine { continuation ->
                context.feature(Messaging::class).conversationManager?.fetchConversationWithMessagesPaginated(conversationId,
                    lastMessageId,
                    amount, onSuccess = { messages ->
                        continuation.resumeWith(Result.success(messages))
                    }, onError = {
                        continuation.resumeWith(Result.success(null))
                    }) ?: continuation.resumeWith(Result.success(null))
            }
            if (messages != null) return@runBlocking messages
            logDialog("Retrying in 1 second...")
            delay(1000)
        }
        logDialog("Failed to fetch messages")
        emptyList()
    }

    private suspend fun exportFullConversation(friendFeedEntry: FriendFeedEntry) {
        //first fetch the first message
        val conversationId = friendFeedEntry.key!!
        val conversationName = friendFeedEntry.feedDisplayName ?: friendFeedEntry.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"
        val conversationParticipants = context.database.getConversationParticipants(friendFeedEntry.key!!)
            ?.mapNotNull {
                context.database.getFriendInfo(it)
            }?.associateBy { it.userId!! } ?: emptyMap()

        val publicFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapEnhance").also { if (!it.exists()) it.mkdirs() }
        val outputFile = publicFolder.resolve("conversation_${conversationName}_${System.currentTimeMillis()}.${exportType!!.extension}")

        logDialog(context.translation.format("chat_export.exporting_message", "conversation" to conversationName))

        val conversationExporter = ConversationExporter(
            context = context,
            friendFeedEntry = friendFeedEntry,
            conversationParticipants = conversationParticipants,
            exportFormat = exportType!!,
            messageTypeFilter = mediaToDownload,
            cacheFolder = publicFolder.resolve("cache").also { if (!it.exists()) it.mkdirs() },
            outputFile = outputFile,
        ).apply { init(); printLog = {
            logDialog(it.toString())
        } }

        var foundMessageCount = 0

        var lastMessageId = fetchMessagesPaginated(conversationId, Long.MAX_VALUE, amount = 1).firstOrNull()?.messageDescriptor?.messageId ?: run {
            logDialog(context.translation["chat_export.no_messages_found"])
            return
        }

        while (true) {
            val fetchedMessages = fetchMessagesPaginated(conversationId, lastMessageId, amount = 500)
            if (fetchedMessages.isEmpty()) break
            foundMessageCount += fetchedMessages.size

            if (amountOfMessages != null && foundMessageCount >= amountOfMessages!!) {
                fetchedMessages.subList(0, amountOfMessages!! - foundMessageCount).reversed().forEach { message ->
                    conversationExporter.readMessage(message)
                }
                break
            }

            fetchedMessages.reversed().forEach { message ->
                conversationExporter.readMessage(message)
            }

            fetchedMessages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor!!.messageId!!
            }
            setStatus("Exporting (found ${foundMessageCount})")
        }

        if (exportType == ExportFormat.HTML) conversationExporter.awaitDownload()
        conversationExporter.close()
        logDialog(context.translation["chat_export.writing_output"])
        dialogLogs.clear()
        logDialog("\n" + context.translation.format("chat_export.exported_to",
            "path" to outputFile.absolutePath.toString()
        ) + "\n")
    }

    private fun exportChatForConversations(conversations: List<FriendFeedEntry>) {
        dialogLogs.clear()
        val jobs = mutableListOf<Job>()

        currentActionDialog = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle(context.translation["chat_export.exporting_chats"])
            .setCancelable(false)
            .setMessage("")
            .create()
        
        val conversationSize = context.translation.format("chat_export.processing_chats", "amount" to conversations.size.toString())
        
        logDialog(conversationSize)

        context.coroutineScope.launch {
            conversations.forEach { conversation ->
                launch {
                    runCatching {
                        exportFullConversation(conversation)
                    }.onFailure {
                        logDialog(context.translation.format("chat_export.export_fail", "conversation" to conversation.key.toString()))
                        logDialog(it.stackTraceToString())
                        CoreLogger.xposedLog(it)
                    }
                }.also { jobs.add(it) }
            }
            jobs.joinAll()
            logDialog(context.translation["chat_export.finished"])
        }.also {
            currentActionDialog?.setButton(DialogInterface.BUTTON_POSITIVE, context.translation["chat_export.dialog_negative_button"]) { dialog, _ ->
                it.cancel()
                jobs.forEach { it.cancel() }
                dialog.dismiss()
            }
        }

        currentActionDialog!!.show()
    }
}