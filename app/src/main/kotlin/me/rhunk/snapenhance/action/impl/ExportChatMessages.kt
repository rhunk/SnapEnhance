package me.rhunk.snapenhance.action.impl

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.export.ExportFormat
import me.rhunk.snapenhance.util.export.MessageExporter
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
class ExportChatMessages : AbstractAction("action.export_chat_messages") {
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

    private val dialogLogs = mutableListOf<String>()
    private var currentActionDialog: AlertDialog? = null

    private var exportType: ExportFormat? = null
    private var mediaToDownload: List<ContentType>? = null

    private fun logDialog(message: String) {
        context.runOnUiThread {
            if (dialogLogs.size > 15) dialogLogs.removeAt(0)
            dialogLogs.add(message)
            Logger.debug("dialog: $message")
            currentActionDialog!!.setMessage(dialogLogs.joinToString("\n"))
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
                .setPositiveButton("OK") { _, _ ->
                    cont.resumeWith(Result.success(mediasToDownload))
                }
                .show()
        }
    }

    override fun run() {
        GlobalScope.launch(Dispatchers.Main) {
            exportType = askExportType() ?: return@launch
            mediaToDownload = if (exportType == ExportFormat.HTML) askMediaToDownload() else null

            val friendFeedEntries = context.database.getFriendFeed(20)
            val selectedConversations = mutableListOf<FriendFeedInfo>()

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["chat_export.select_conversation"])
                .setMultiChoiceItems(
                    friendFeedEntries.map { it.feedDisplayName ?: it.friendDisplayName!!.split("|").firstOrNull() }.toTypedArray(),
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
            100,
            callback
        )
    }

    private suspend fun exportFullConversation(friendFeedInfo: FriendFeedInfo) {
        //first fetch the first message
        val conversationId = friendFeedInfo.key!!
        val conversationName = friendFeedInfo.feedDisplayName ?: friendFeedInfo.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"

        conversationAction(true, conversationId, if (friendFeedInfo.feedDisplayName != null) "USERCREATEDGROUP" else "ONEONONE")
        
        logDialog(context.translation.format("chat_export.exporting_message", "conversation" to conversationName))

        val foundMessages = fetchMessagesPaginated(conversationId, Long.MAX_VALUE).toMutableList()
        var lastMessageId = foundMessages.firstOrNull()?.messageDescriptor?.messageId ?: run {
            logDialog(context.translation["chat_export.no_messages_found"])
            return
        }

        while (true) {
            Logger.debug("[$conversationName] fetching $lastMessageId")
            val messages = fetchMessagesPaginated(conversationId, lastMessageId)
            if (messages.isEmpty()) break
            foundMessages.addAll(messages)
            messages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor.messageId
            }
        }

        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnapEnhance/conversation_${conversationName}_${System.currentTimeMillis()}.${exportType!!.extension}"
        ).also { it.parentFile?.mkdirs() }

        logDialog(context.translation["chat_export.writing_output"])
        MessageExporter(
            context = context,
            friendFeedInfo = friendFeedInfo,
            outputFile = outputFile,
            mediaToDownload = mediaToDownload,
            printLog = ::logDialog
        ).also {
            runCatching {
                it.readMessages(foundMessages)
            }.onFailure {
                logDialog(context.translation.format("chat_export.export_failed","conversation" to it.message.toString()))
                Logger.error(it)
                return
            }
        }.exportTo(exportType!!)

        dialogLogs.clear()
        logDialog("\n" + context.translation.format("chat_export.exported_to",
            "path" to outputFile.absolutePath.toString()
        ) + "\n")

        currentActionDialog?.setButton(DialogInterface.BUTTON_POSITIVE, "Open") { _, _ ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(outputFile.parentFile), "resource/folder")
            context.mainActivity!!.startActivity(intent)
        }

        runCatching {
            conversationAction(false, conversationId, null)
        }
    }

    private fun exportChatForConversations(conversations: List<FriendFeedInfo>) {
        dialogLogs.clear()
        val jobs = mutableListOf<Job>()

        currentActionDialog = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle(context.translation["chat_export.exporting_chats"])
            .setCancelable(false)
            .setMessage("")
            .setNegativeButton(context.translation["chat_export.dialog_negative_button"]) { dialog, _ ->
                jobs.forEach { it.cancel() }
                dialog.dismiss()
            }
            .create()
        
        val conversationSize = context.translation.format("chat_export.processing_chats", "amount" to conversations.size.toString())
        
        logDialog(conversationSize)

        currentActionDialog!!.show()

        GlobalScope.launch(Dispatchers.Default) {
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
        }
    }
}