package me.rhunk.snapenhance.action.impl

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Environment
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.core.Logger
import me.rhunk.snapenhance.core.database.objects.FriendFeedEntry
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.util.export.ExportFormat
import me.rhunk.snapenhance.core.util.export.MessageExporter
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import java.io.File
import kotlin.math.absoluteValue

class ExportChatMessages : AbstractAction() {
    private val callbackClass by lazy {  context.mappings.getMappedClass("callbacks", "Callback") }

    private val fetchConversationWithMessagesCallbackClass by lazy {  context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback") }

    private val enterConversationMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "enterConversation" }
    }
    private val exitConversationMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "exitConversation" }
    }
    private val fetchConversationWithMessagesPaginatedMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessagesPaginated" }
    }

    private val conversationManagerInstance by lazy {
        context.feature(Messaging::class).conversationManager
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

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
                .setItems(ExportFormat.values().map { it.name }.toTypedArray()) { _, which ->
                    cont.resumeWith(Result.success(ExportFormat.values()[which]))
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .show()
        }
    }

    private suspend fun askAmountOfMessages() = suspendCancellableCoroutine { cont ->
        coroutineScope.launch(Dispatchers.Main) {
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
        coroutineScope.launch(Dispatchers.Main) {
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

    private suspend fun conversationAction(isEntering: Boolean, conversationId: String, conversationType: String?) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(callbackClass)
            .override("onSuccess") { _ ->
                continuation.resumeWith(Result.success(Unit))
            }
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to ${if (isEntering) "enter" else "exit"} conversation")))
            }.build()

        if (isEntering) {
            enterConversationMethod.invoke(
                conversationManagerInstance,
                SnapUUID.fromString(conversationId).instanceNonNull(),
                enterConversationMethod.parameterTypes[1].enumConstants.first { it.toString() == conversationType },
                callback
            )
        } else {
            exitConversationMethod.invoke(
                conversationManagerInstance,
                SnapUUID.fromString(conversationId).instanceNonNull(),
                Long.MAX_VALUE,
                callback
            )
        }
    }

    private suspend fun fetchMessagesPaginated(conversationId: String, lastMessageId: Long) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass)
            .override("onFetchConversationWithMessagesComplete") { param ->
                val messagesList = param.arg<List<*>>(1).map { Message(it) }
                continuation.resumeWith(Result.success(messagesList))
            }
            .override("onServerRequest", shouldUnhook = false) {}
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to fetch messages")))
            }.build()

        fetchConversationWithMessagesPaginatedMethod.invoke(
            conversationManagerInstance,
            SnapUUID.fromString(conversationId).instanceNonNull(),
            lastMessageId,
            500,
            callback
        )
    }

    private suspend fun exportFullConversation(friendFeedEntry: FriendFeedEntry) {
        //first fetch the first message
        val conversationId = friendFeedEntry.key!!
        val conversationName = friendFeedEntry.feedDisplayName ?: friendFeedEntry.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"

        runCatching {
            conversationAction(true, conversationId, if (friendFeedEntry.feedDisplayName != null) "USERCREATEDGROUP" else "ONEONONE")
        }

        logDialog(context.translation.format("chat_export.exporting_message", "conversation" to conversationName))

        val foundMessages = fetchMessagesPaginated(conversationId, Long.MAX_VALUE).toMutableList()
        var lastMessageId = foundMessages.firstOrNull()?.messageDescriptor?.messageId ?: run {
            logDialog(context.translation["chat_export.no_messages_found"])
            return
        }

        while (true) {
            val messages = fetchMessagesPaginated(conversationId, lastMessageId)
            if (messages.isEmpty()) break

            if (amountOfMessages != null && messages.size + foundMessages.size >= amountOfMessages!!) {
                foundMessages.addAll(messages.take(amountOfMessages!! - foundMessages.size))
                break
            }

            foundMessages.addAll(messages)
            messages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor.messageId
            }
            setStatus("Exporting (${foundMessages.size} / ${foundMessages.firstOrNull()?.orderKey})")
        }

        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnapEnhance/conversation_${conversationName}_${System.currentTimeMillis()}.${exportType!!.extension}"
        ).also { it.parentFile?.mkdirs() }

        logDialog(context.translation["chat_export.writing_output"])

        runCatching {
            MessageExporter(
                context = context,
                friendFeedEntry = friendFeedEntry,
                outputFile = outputFile,
                mediaToDownload = mediaToDownload,
                printLog = ::logDialog
            ).apply { readMessages(foundMessages) }.exportTo(exportType!!)
        }.onFailure {
            logDialog(context.translation.format("chat_export.export_failed","conversation" to it.message.toString()))
            context.log.error("Failed to export conversation $conversationName", it)
            return
        }

        dialogLogs.clear()
        logDialog("\n" + context.translation.format("chat_export.exported_to",
            "path" to outputFile.absolutePath.toString()
        ) + "\n")

        runCatching {
            conversationAction(false, conversationId, null)
        }
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

        coroutineScope.launch {
            conversations.forEach { conversation ->
                launch {
                    runCatching {
                        exportFullConversation(conversation)
                    }.onFailure {
                        logDialog(context.translation.format("chat_export.export_fail", "conversation" to conversation.key.toString()))
                        logDialog(it.stackTraceToString())
                        Logger.xposedLog(it)
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