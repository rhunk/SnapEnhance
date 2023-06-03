package me.rhunk.snapenhance.action.impl

import android.app.AlertDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.database.objects.FriendFeedInfo
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.util.CallbackBuilder
import kotlin.concurrent.thread

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
    private val fetchConversationWithMessagesMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessages" }
    }

    private val conversationManagerInstance by lazy {
        context.feature(Messaging::class).conversationManager
    }

    private val dialogLogs = mutableListOf<String>()
    private var currentActionDialog: AlertDialog? = null

    private fun logDialog(message: String) {
        context.runOnUiThread {
            if (dialogLogs.size > 50) dialogLogs.removeAt(0)
            dialogLogs.add(message)
            currentActionDialog!!.setMessage(dialogLogs.joinToString("\n"))
        }
    }

    override fun run() {
        val friendFeedEntries = context.database.getFriendFeed()

        val selectedConversations = mutableListOf<FriendFeedInfo>()

        AlertDialog.Builder(context.mainActivity)
            .setTitle("Select a conversation")
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
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .setNeutralButton("Export all") { dialog, which ->
                exportChatForConversations(friendFeedEntries)
            }
            .setPositiveButton("Export") { dialog, which ->
                exportChatForConversations(selectedConversations)
            }
            .show()
    }

    private suspend fun conversationAction(isEntering: Boolean, conversationId: String, conversationType: String?) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(callbackClass)
            .override("onSuccess") { param ->
                continuation.resumeWith(Result.success(Unit))
            }
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to enter conversation")))
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


    private suspend fun fetchMessages(conversationId: String) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass)
            .override("onFetchConversationWithMessagesComplete") { param ->
                val messagesList = param.arg<List<*>>(1).map { Message(it) }
                continuation.resumeWith(Result.success(messagesList))
            }
            .override("onError") {
                continuation.resumeWith(Result.failure(Exception("Failed to fetch messages")))
            }.build()

        fetchConversationWithMessagesMethod.invoke(
            conversationManagerInstance,
            SnapUUID.fromString(conversationId).instanceNonNull(),
            callback
        )
    }

    private suspend fun fetchMessagesPaginated(conversationId: String, lastMessageId: Long) = suspendCancellableCoroutine { continuation ->
        val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass)
            .override("onFetchConversationWithMessagesComplete") { param ->
                val messagesList = param.arg<List<*>>(1).map { Message(it) }
                val isLastMessage = param.arg<Boolean>(2)
                continuation.resumeWith(Result.success(Pair(messagesList, isLastMessage)))
            }
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

    private suspend fun exportFullConversation(conversation: FriendFeedInfo) {
        //first fetch the first message
        val conversationId = conversation.key!!
        conversationAction(true, conversationId, if (conversation.feedDisplayName != null) "USERCREATEDGROUP" else "ONEONONE")

        val foundMessages = fetchMessages(conversationId).toMutableList()
        var lastMessageId = foundMessages.firstOrNull()?.messageDescriptor?.messageId ?: run {
            Logger.debug("No messages found")
            return
        }

        Logger.log("initial message list size ${foundMessages.size}")

        while (true) {
            Logger.debug("Fetching messages from $lastMessageId")
            val (messages, isLast) = fetchMessagesPaginated(conversationId, lastMessageId)
            Logger.debug("isLast $isLast")
            if (messages.isEmpty()) {
                Logger.debug("No more messages")
                break
            }
            foundMessages.addAll(messages)
            Logger.debug("addAll $lastMessageId => ${messages.size}")
            messages.lastOrNull()?.let {
                lastMessageId = it.messageDescriptor.messageId
                Logger.debug("Last message id ${it.messageDescriptor.messageId}")
            }
        }

        foundMessages.forEach {
            Logger.debug("${it.messageContent.contentType} " + it.messageContent.content.contentToString())
        }
        Logger.debug("size ${foundMessages.size}")
        conversationAction(false, conversationId, null)
    }

    private fun exportChatForConversations(conversations: List<FriendFeedInfo>) {

        val jobs = mutableListOf<Job>()

        currentActionDialog = AlertDialog.Builder(context.mainActivity)
            .setTitle("Exporting chats")
            .setCancelable(false)
            .setNegativeButton("Cancel") { dialog, which ->
                jobs.forEach { it.cancel() }
                dialog.dismiss()
            }
            .create()

        logDialog("Exporting ${conversations.size} conversations")

        currentActionDialog!!.show()

        thread(start = true) {
            runBlocking {
                conversations.forEach { conversation ->
                    jobs.add(launch {
                        exportFullConversation(conversation)
                        logDialog("Conversation ${conversation.key} exported")
                    })
                }
                jobs.forEach { it.join() }
                logDialog("Done!")
            }
        }
    }
}