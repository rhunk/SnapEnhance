package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper

class MessagingPreview(
    private val context: RemoteSideContext,
    private val scope: SocialScope,
    private val scopeId: String
) {
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var messagingBridge: MessagingBridge
    private lateinit var previewScrollState: LazyListState
    private var conversationId: String? = null
    private val messages = sortedMapOf<Long, Message>()
    private var messageSize by mutableIntStateOf(0)
    private var lastMessageId = Long.MAX_VALUE

    @Composable
    private fun ConversationPreview() {
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
                val messageReader = ProtoReader(messages.entries.elementAt(index).value.content)
                val contentType = ContentType.fromMessageContainer(messageReader)

                Card(
                    modifier = Modifier
                        .padding(5.dp)
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