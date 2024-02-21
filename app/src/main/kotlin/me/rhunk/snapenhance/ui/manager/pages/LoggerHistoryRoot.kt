package me.rhunk.snapenhance.ui.manager.pages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.common.bridge.wrapper.LoggedMessage
import me.rhunk.snapenhance.common.bridge.wrapper.LoggerWrapper
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.download.*
import me.rhunk.snapenhance.common.util.ktx.copyToClipboard
import me.rhunk.snapenhance.common.util.ktx.longHashCode
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.DecodedAttachment
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.download.DownloadProcessor
import me.rhunk.snapenhance.ui.manager.Routes
import kotlin.math.absoluteValue


class LoggerHistoryRoot : Routes.Route() {
    private lateinit var loggerWrapper: LoggerWrapper
    private var selectedConversation by mutableStateOf<String?>(null)
    private var stringFilter by mutableStateOf("")
    private var reverseOrder by mutableStateOf(true)

    private inline fun decodeMessage(message: LoggedMessage, result: (contentType: ContentType, messageReader: ProtoReader, attachments: List<DecodedAttachment>) -> Unit) {
        runCatching {
            val messageObject = JsonParser.parseString(String(message.messageData, Charsets.UTF_8)).asJsonObject
            val messageContent = messageObject.getAsJsonObject("mMessageContent")
            val messageReader = messageContent.getAsJsonArray("mContent").map { it.asByte }.toByteArray().let { ProtoReader(it) }
            result(ContentType.fromMessageContainer(messageReader) ?: ContentType.UNKNOWN, messageReader, MessageDecoder.decode(messageContent))
        }.onFailure {
            context.log.error("Failed to decode message", it)
        }
    }

    private fun downloadAttachment(creationTimestamp: Long, attachment: DecodedAttachment) {
        context.shortToast("Download started!")
        val attachmentHash = attachment.mediaUniqueId!!.longHashCode().absoluteValue.toString()

        DownloadProcessor(
            remoteSideContext = context,
            callback = object: DownloadCallback.Default() {
                override fun onSuccess(outputPath: String?) {
                    context.shortToast("Downloaded to $outputPath")
                }

                override fun onFailure(message: String?, throwable: String?) {
                    context.shortToast("Failed to download $message")
                }
            }
        ).enqueue(
            DownloadRequest(
                inputMedias = arrayOf(
                    InputMedia(
                        content = attachment.mediaUrlKey!!,
                        type = DownloadMediaType.PROTO_MEDIA,
                        encryption = attachment.attachmentInfo?.encryption,
                    )
                )
            ),
            DownloadMetadata(
                mediaIdentifier = attachmentHash,
                outputPath = createNewFilePath(
                    context.config.root,
                    attachment.mediaUniqueId!!,
                    MediaDownloadSource.MESSAGE_LOGGER,
                    attachmentHash,
                    creationTimestamp
                ),
                iconUrl = null,
                mediaAuthor = null,
                downloadSource = MediaDownloadSource.MESSAGE_LOGGER.translate(context.translation),
            )
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun MessageView(message: LoggedMessage) {
        var contentView by remember { mutableStateOf<@Composable () -> Unit>({
            Spacer(modifier = Modifier.height(30.dp))
        }) }

        OutlinedCard(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                contentView()

                LaunchedEffect(Unit, message) {
                    runCatching {
                        decodeMessage(message) { contentType, messageReader, attachments ->
                            if (contentType == ContentType.CHAT) {
                                val content = messageReader.getString(2, 1) ?: "[empty chat message]"
                                contentView = {
                                    Text(content, modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectTapGestures(onLongPress = {
                                                context.androidContext.copyToClipboard(content)
                                            })
                                        })
                                }
                                return@runCatching
                            }
                            contentView = {
                                Column column@{
                                    Text("[$contentType]")
                                    if (attachments.isEmpty()) return@column

                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        attachments.forEachIndexed { index, attachment ->
                                            ElevatedButton(onClick = {
                                                context.coroutineScope.launch {
                                                    runCatching {
                                                        downloadAttachment(message.timestamp, attachment)
                                                    }.onFailure {
                                                        context.log.error("Failed to download attachment", it)
                                                        context.shortToast("Failed to download attachment")
                                                    }
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download",
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                Text("Attachment ${index + 1}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure {
                        context.log.error("Failed to parse message", it)
                        contentView = {
                            Text("[Failed to parse message]")
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        LaunchedEffect(Unit) {
            loggerWrapper = LoggerWrapper(
                context.androidContext.getDatabasePath("message_logger.db")
            )
        }

        Column {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selectedConversation ?: "Select a conversation",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                val conversations = remember { mutableStateListOf<String>() }

                LaunchedEffect(Unit) {
                    conversations.clear()
                    withContext(Dispatchers.IO) {
                        conversations.addAll(loggerWrapper.getAllConversations())
                    }
                }

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    conversations.forEach { conversation ->
                        DropdownMenuItem(onClick = {
                            selectedConversation = conversation
                            expanded = false
                        }, text = {
                            Text(conversation)
                        })
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Reverse order")
                    Checkbox(checked = reverseOrder, onCheckedChange = {
                        reverseOrder = it
                    })
                }
            }

            var hasReachedEnd by remember(selectedConversation, stringFilter, reverseOrder) { mutableStateOf(false) }
            var lastFetchMessageTimestamp by remember(selectedConversation, stringFilter, reverseOrder) { mutableLongStateOf(if (reverseOrder) Long.MAX_VALUE else Long.MIN_VALUE) }
            val messages = remember(selectedConversation, stringFilter, reverseOrder) { mutableStateListOf<LoggedMessage>() }

            LazyColumn {
                items(messages) { message ->
                    MessageView(message)
                }
                item {
                    if (selectedConversation != null) {
                        if (hasReachedEnd) {
                            Text("No more messages", modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(), textAlign = TextAlign.Center)
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                    LaunchedEffect(Unit, selectedConversation, stringFilter, reverseOrder) {
                        withContext(Dispatchers.IO) {
                            val newMessages = loggerWrapper.fetchMessages(
                                selectedConversation ?: return@withContext,
                                lastFetchMessageTimestamp,
                                30,
                                reverseOrder
                            ) { messageData ->
                                if (stringFilter.isEmpty()) return@fetchMessages true
                                var isMatch = false
                                decodeMessage(messageData) { contentType, messageReader, _ ->
                                    if (contentType == ContentType.CHAT) {
                                        val content = messageReader.getString(2, 1) ?: return@decodeMessage
                                        isMatch = content.contains(stringFilter, ignoreCase = true)
                                    }
                                }
                                isMatch
                            }
                            if (newMessages.isEmpty()) {
                                hasReachedEnd = true
                                return@withContext
                            }
                            lastFetchMessageTimestamp = newMessages.lastOrNull()?.timestamp ?: return@withContext
                            withContext(Dispatchers.Main) {
                                messages.addAll(newMessages)
                            }
                        }
                    }
                }
            }
        }
    }

    override val topBarActions: @Composable (RowScope.() -> Unit) = {
        val focusRequester = remember { FocusRequester() }
        var showSearchTextField by remember { mutableStateOf(false) }

        if (showSearchTextField) {
            var searchValue by remember { mutableStateOf("") }

            TextField(
                value = searchValue,
                onValueChange = { keyword ->
                    searchValue = keyword
                },
                keyboardActions = KeyboardActions(onDone = { focusRequester.freeFocus() }),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .weight(1f, fill = true)
                    .padding(end = 10.dp)
                    .height(70.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            ElevatedButton(onClick = {
                stringFilter = searchValue
            }) {
                Text("Search")
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        IconButton(onClick = {
            showSearchTextField = !showSearchTextField
            stringFilter = ""
        }) {
            Icon(
                imageVector = if (showSearchTextField) Icons.Filled.Close
                else Icons.Filled.Search,
                contentDescription = null
            )
        }
    }
}