package me.rhunk.snapenhance.messaging

import androidx.compose.runtime.MutableIntState
import kotlinx.coroutines.delay
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.common.data.ContentType
import kotlin.random.Random


enum class MessagingTaskType(
    val key: String
) {
    SAVE("SAVE"),
    UNSAVE("UNSAVE"),
    DELETE("ERASE"),
    READ("READ"),
}

typealias MessagingTaskConstraint = Message.() -> Boolean

object MessagingConstraints {
    val USER_ID: (String) -> MessagingTaskConstraint = { userId: String ->
        {
            this.senderId == userId
        }
    }
    val NO_USER_ID: (String) -> MessagingTaskConstraint = { userId: String ->
        {
            this.senderId != userId
        }
    }
    val MY_USER_ID: (messagingBridge: MessagingBridge) -> MessagingTaskConstraint = {
        val myUserId = it.myUserId
        {
            this.senderId == myUserId
        }
    }
    val CONTENT_TYPE: (Array<ContentType>) -> MessagingTaskConstraint = {
        val contentTypes = it.map { type -> type.id };
        {
            contentTypes.contains(this.contentType)
        }
    }
}

class MessagingTask(
    private val messagingBridge: MessagingBridge,
    private val conversationId: String,
    val taskType: MessagingTaskType,
    val constraints: List<MessagingTaskConstraint>,
    private val processedMessageCount: MutableIntState,
    val onSuccess: (message: Message) -> Unit = {},
    private val onFailure: (message: Message, reason: String) -> Unit = { _, _ -> },
    private val overrideClientMessageIds: List<Long>? = null,
    private val amountToProcess: Int? = null,
) {
    private suspend fun processMessages(
        messages: List<Message>
    ) {
        messages.forEach { message ->
            if (constraints.any { !it(message) }) {
                return@forEach
            }

            val error = messagingBridge.updateMessage(conversationId, message.clientMessageId, taskType.key)
            error?.takeIf { error != "DUPLICATE_REQUEST" }?.let {
                onFailure(message, error)
            }
            onSuccess(message)
            processedMessageCount.intValue++
            delay(Random.nextLong(20, 50))
        }
    }

    fun hasFixedGoal() = overrideClientMessageIds?.takeIf { it.isNotEmpty() } != null || amountToProcess?.takeIf { it > 0 } != null

    suspend fun run() {
        var processedOverrideMessages = 0
        var lastMessageId = Long.MAX_VALUE

        do {
            val fetchedMessages = messagingBridge.fetchConversationWithMessagesPaginated(
                conversationId,
                100,
                lastMessageId
            ) ?: return

            if (fetchedMessages.isEmpty()) {
                break
            }

            lastMessageId = fetchedMessages.first().clientMessageId

            overrideClientMessageIds?.let { ids ->
                fetchedMessages.retainAll { message ->
                    ids.contains(message.clientMessageId)
                }
            }

            amountToProcess?.let { amount ->
                while (processedMessageCount.intValue + fetchedMessages.size > amount) {
                    fetchedMessages.removeLastOrNull()
                }
            }

            processMessages(fetchedMessages.reversed())

            overrideClientMessageIds?.let { ids ->
                processedOverrideMessages += fetchedMessages.count { message ->
                    ids.contains(message.clientMessageId)
                }

                if (processedOverrideMessages >= ids.size) {
                    return
                }
            }

            amountToProcess?.let { amount ->
                if (processedMessageCount.intValue >= amount) {
                    return
                }
            }
        } while (true)
    }
}