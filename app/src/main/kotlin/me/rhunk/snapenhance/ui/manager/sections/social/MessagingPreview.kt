package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.Dialog
import kotlin.random.Random

class MessagingPreview(
    private val context: RemoteSideContext,
    private val scope: SocialScope,
    private val scopeId: String
) {
    private val alertDialogs by lazy { AlertDialogs(context.translation) }

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var messagingBridge: MessagingBridge
    private lateinit var previewScrollState: LazyListState
    private val myUserId by lazy { messagingBridge.myUserId }

    private var conversationId: String? = null
    private val messages = sortedMapOf<Long, Message>()
    private var messageSize by mutableIntStateOf(0)
    private var lastMessageId = Long.MAX_VALUE
    private val selectedMessages = mutableStateListOf<Long>()

    private fun toggleSelectedMessage(messageId: Long) {
        if (selectedMessages.contains(messageId)) selectedMessages.remove(messageId)
        else selectedMessages.add(messageId)
    }


    @Composable
    fun TopBarAction() {
        var deletedMessageCount by remember { mutableIntStateOf(0) }
        var messageDeleteJob by remember { mutableStateOf(null as Job?) }

        fun deleteIndividualMessage(serverMessageId: Long) {
            val message = messages[serverMessageId] ?: return
            if (message.senderId != myUserId) return

            val error = messagingBridge.updateMessage(conversationId, message.clientMessageId, "ERASE")

            if (error != null) {
                context.shortToast("Failed to delete message: $error")
            } else {
                coroutineScope.launch {
                    deletedMessageCount++
                    messages.remove(serverMessageId)
                    messageSize = messages.size
                }
            }
        }

        if (messageDeleteJob != null) {
            Dialog(onDismissRequest = {
                messageDeleteJob?.cancel()
                messageDeleteJob = null
            }) {
                Card {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Deleting messages ($deletedMessageCount)")
                        Spacer(modifier = Modifier.height(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding()
                                .size(30.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }


        if (selectedMessages.isNotEmpty()) {
            IconButton(onClick = {
                deletedMessageCount = 0
                messageDeleteJob = coroutineScope.launch(Dispatchers.IO) {
                    selectedMessages.toList().also {
                        selectedMessages.clear()
                    }.forEach { messageId ->
                        deleteIndividualMessage(messageId)
                    }
                }.apply {
                    invokeOnCompletion {
                        context.shortToast("Successfully deleted $deletedMessageCount messages")
                        messageDeleteJob = null
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete"
                )
            }

            IconButton(onClick = {
                selectedMessages.clear()
            }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close"
                )
            }
        } else {
            var deleteAllConfirmationDialog by remember { mutableStateOf(false) }

            if (deleteAllConfirmationDialog) {
                Dialog(onDismissRequest = { deleteAllConfirmationDialog = false }) {
                    alertDialogs.ConfirmDialog(
                        title = "Are you sure you want to delete all your messages?",
                        message = "Warning: This action may flag your account for spam if used excessively.",
                        onDismiss = {
                        deleteAllConfirmationDialog = false
                    }, onConfirm = {
                        deletedMessageCount = 0
                        deleteAllConfirmationDialog = false
                        messageDeleteJob = coroutineScope.launch(Dispatchers.IO) {
                            var lastMessageId = Long.MAX_VALUE

                            do {
                                val fetchedMessages = messagingBridge.fetchConversationWithMessagesPaginated(
                                    conversationId!!,
                                    100,
                                    lastMessageId
                                )

                                if (fetchedMessages == null) {
                                    context.shortToast("Failed to fetch messages")
                                    return@launch
                                }

                                if (fetchedMessages.isEmpty()) {
                                    break
                                }

                                fetchedMessages.forEach {
                                    deleteIndividualMessage(it.serverMessageId)
                                    delay(Random.nextLong(50, 170))
                                }

                                lastMessageId = fetchedMessages.first().clientMessageId
                            } while (true)
                        }.apply {
                            invokeOnCompletion {
                                messageDeleteJob = null
                                context.shortToast("Successfully deleted $deletedMessageCount messages")
                            }
                        }
                    })
                }
            }

            IconButton(onClick = {
                deleteAllConfirmationDialog = true
            }) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = "Delete"
                )
            }
        }
    }

    @Composable
    private fun ConversationPreview() {
        DisposableEffect(Unit) {
            onDispose {
                selectedMessages.clear()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = previewScrollState,
        ) {
            item {
                if (messages.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("No messages")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                LaunchedEffect(Unit) {
                    if (messages.size > 0) {
                        fetchNewMessages()
                    }
                }
            }
            items(messageSize) {index ->
                val elementKey = remember(index) { messages.entries.elementAt(index).key }
                val messageReader = ProtoReader(messages.entries.elementAt(index).value.content)
                val contentType = ContentType.fromMessageContainer(messageReader)

                Card(
                    modifier = Modifier
                        .padding(5.dp)
                        .pointerInput(Unit) {
                            if (contentType == ContentType.STATUS) return@pointerInput
                            detectTapGestures(
                                onLongPress = {
                                    toggleSelectedMessage(elementKey)
                                },
                                onTap = {
                                    if (selectedMessages.isNotEmpty()) {
                                        toggleSelectedMessage(elementKey)
                                    }
                                }
                            )
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMessages.contains(elementKey)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(5.dp)
                    ) {

                        Text("[$contentType] ${messageReader.getString(2, 1) ?: ""}")
                    }
                }
            }
        }
    }

    private fun fetchNewMessages() {
        coroutineScope.launch(Dispatchers.IO) cs@{
            runCatching {
                val queriedMessages = messagingBridge.fetchConversationWithMessagesPaginated(
                    conversationId!!,
                    100,
                    lastMessageId
                )

                if (queriedMessages == null) {
                    context.shortToast("Failed to fetch messages")
                    return@cs
                }

                coroutineScope.launch {
                    messages.putAll(queriedMessages.map { it.serverMessageId to it })
                    messageSize = messages.size
                    if (queriedMessages.isNotEmpty()) {
                        lastMessageId = queriedMessages.first().clientMessageId
                        previewScrollState.scrollToItem(queriedMessages.size - 1)
                    }
                }
            }.onFailure {
                context.shortToast("Failed to fetch messages: ${it.message}")
            }
            context.log.verbose("fetched ${messages.size} messages")
        }
    }

    private fun onMessagingBridgeReady() {
        messagingBridge = context.bridgeService!!.messagingBridge!!
        conversationId = if (scope == SocialScope.FRIEND) messagingBridge.getOneToOneConversationId(scopeId) else scopeId
        if (conversationId == null) {
            context.longToast("Failed to fetch conversation id")
            return
        }
        fetchNewMessages()
    }

    @Composable
    private fun LoadingRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding()
                    .size(30.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    @Composable
    fun Content() {
        previewScrollState = rememberLazyListState()
        coroutineScope = rememberCoroutineScope()
        var isBridgeConnected by remember { mutableStateOf(false) }
        var hasBridgeError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LaunchedEffect(Unit) {
                isBridgeConnected = context.hasMessagingBridge()
                if (isBridgeConnected) {
                    onMessagingBridgeReady()
                } else {
                    SnapWidgetBroadcastReceiverHelper.create("wakeup") {}.also {
                        context.androidContext.sendBroadcast(it)
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        withTimeout(10000) {
                            while (!context.hasMessagingBridge()) {
                                delay(100)
                            }
                            isBridgeConnected = true
                            onMessagingBridgeReady()
                        }
                    }.invokeOnCompletion {
                        if (it != null) {
                            hasBridgeError = true
                        }
                    }
                }
            }

            if (hasBridgeError) {
                Text("Failed to connect to Snapchat through bridge service")
            }

            if (!isBridgeConnected && !hasBridgeError) {
                LoadingRow()
            }

            if (isBridgeConnected && !hasBridgeError) {
                ConversationPreview()
            }
        }
    }
}