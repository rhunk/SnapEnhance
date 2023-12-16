package me.rhunk.snapenhance.core.action.impl

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Environment
import android.view.WindowManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.messaging.ConversationExporter
import me.rhunk.snapenhance.core.messaging.ExportFormat
import me.rhunk.snapenhance.core.messaging.ExportParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.wrapper.impl.Message
import java.io.File
import kotlin.math.absoluteValue

class ExportChatMessages : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("chat_export") }
    private val dialogLogs = mutableListOf<String>()
    private var currentActionDialog: AlertDialog? = null

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

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    private fun ExporterDialog(
        getDialog: () -> AlertDialog? = { null }
    ) {
        val exporterTranslation = remember {
            translation.getCategory("exporter_dialog")
        }

        var feedEntries by remember { mutableStateOf(emptyList<FriendFeedEntry>()) }
        var exportType by remember { mutableStateOf(ExportFormat.HTML) }
        val selectedFeedEntries = remember { mutableStateListOf<FriendFeedEntry>() }
        val messageTypeFilter = remember { mutableStateListOf<ContentType>() }
        var amountOfMessages by remember { mutableIntStateOf(-1) }
        var downloadMedias by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(remember { ScrollState(0) })
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(exporterTranslation["select_conversations_title"])
            run {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    TextField(
                        value = selectedFeedEntries.let {
                            exporterTranslation.format("text_field_selection", "amount" to it.size.toString())
                        },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LazyColumn(
                            modifier = Modifier.size(LocalConfiguration.current.screenWidthDp.dp, 300.dp)
                        ) {
                            items(feedEntries) { feedEntry ->
                                DropdownMenuItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (selectedFeedEntries.contains(feedEntry)) selectedFeedEntries -= feedEntry
                                        else selectedFeedEntries += feedEntry
                                    },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = selectedFeedEntries.contains(feedEntry), onCheckedChange = null)
                                            Text(
                                                text = feedEntry.feedDisplayName ?: feedEntry.friendDisplayName ?: "unknown",
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text(exporterTranslation["export_file_format_title"])
            run {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    TextField(
                        value = exportType.extension,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExportFormat.entries.forEach { exportFormat ->
                            DropdownMenuItem(onClick = {
                                exportType = exportFormat
                                expanded = false
                            }, text = {
                                Text(text = exportFormat.name)
                            })
                        }
                    }
                }
            }
            Text(exporterTranslation["message_type_filter_title"])
            run {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    TextField(
                        value = messageTypeFilter.takeIf { it.isNotEmpty() }?.let {
                            exporterTranslation.format("text_field_selection", "amount" to it.size.toString())
                        } ?: exporterTranslation["text_field_selection_all"],
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        arrayOf(
                            ContentType.CHAT,
                            ContentType.SNAP,
                            ContentType.EXTERNAL_MEDIA,
                            ContentType.NOTE,
                            ContentType.STICKER
                        ).forEach { contentType ->
                            DropdownMenuItem(onClick = {
                                if (messageTypeFilter.contains(contentType)) messageTypeFilter -= contentType
                                else messageTypeFilter += contentType
                            }, text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = messageTypeFilter.contains(contentType), onCheckedChange = null)
                                    Text(text = contentType.name)
                                }
                            })
                        }
                    }
                }
            }

            Text(exporterTranslation["amount_of_messages_title"])
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextField(
                value = amountOfMessages.takeIf { it != -1 }?.toString() ?: "",
                onValueChange = { amountOfMessages = it.toIntOrNull()?.absoluteValue ?: -1 },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = downloadMedias, onCheckedChange = { downloadMedias = it })
                Text(exporterTranslation["download_medias_title"])
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = { getDialog()?.dismiss() }
                ) {
                    Text(text = translation["dialog_negative_button"])
                }
                Button(
                    enabled = selectedFeedEntries.isNotEmpty(),
                    onClick = {
                        exportChatForConversations(selectedFeedEntries, ExportParams(
                            exportFormat = exportType,
                            messageTypeFilter = messageTypeFilter.takeIf { it.isNotEmpty() },
                            amountOfMessages = amountOfMessages.takeIf { it != -1 },
                            downloadMedias = downloadMedias
                        ))
                    }
                ) {
                    Text(text = translation["dialog_positive_button"])
                }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    feedEntries = context.database.getFeedEntries(500)
                }
            }
        }
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            lateinit var exporterDialog: AlertDialog
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(translation["select_conversation"])
                .setView(createComposeView(context.mainActivity!!) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        ExporterDialog { exporterDialog }
                    }
                })
                .create().apply {
                    exporterDialog = this
                    setCanceledOnTouchOutside(false)
                    show()
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                }
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

    private fun exportChatForConversations(
        conversations: List<FriendFeedEntry>,
        exportParams: ExportParams,
    ) {
        dialogLogs.clear()
        val jobs = mutableListOf<Job>()

        currentActionDialog = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle(translation["exporting_chats"])
            .setCancelable(false)
            .setMessage("")
            .create()
        
        val conversationSize = translation.format("processing_chats", "amount" to conversations.size.toString())
        
        logDialog(conversationSize)

        context.coroutineScope.launch {
            conversations.forEach { conversation ->
                launch {
                    runCatching {
                        exportFullConversation(conversation, exportParams)
                    }.onFailure {
                        logDialog(translation.format("export_fail", "conversation" to conversation.key.toString()))
                        logDialog(it.stackTraceToString())
                        CoreLogger.xposedLog(it)
                    }
                }.also { jobs.add(it) }
            }
            jobs.joinAll()
            logDialog(translation["finished"])
        }.also {
            currentActionDialog?.setButton(DialogInterface.BUTTON_POSITIVE, translation["dialog_negative_button"]) { dialog, _ ->
                it.cancel()
                jobs.forEach { it.cancel() }
                dialog.dismiss()
            }
        }

        currentActionDialog!!.also {
            it.setCanceledOnTouchOutside(false)
        }.show()
    }

    private suspend fun exportFullConversation(
        feedEntry: FriendFeedEntry,
        exportParams: ExportParams,
    ) {
        //first fetch the first message
        val conversationId = feedEntry.key!!
        val conversationName = feedEntry.feedDisplayName ?: feedEntry.friendDisplayName!!.split("|").lastOrNull() ?: "unknown"
        val conversationParticipants = context.database.getConversationParticipants(feedEntry.key!!)
            ?.mapNotNull {
                context.database.getFriendInfo(it)
            }?.associateBy { it.userId!! } ?: emptyMap()

        val publicFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapEnhance").also { if (!it.exists()) it.mkdirs() }
        val outputFile = publicFolder.resolve("conversation_${conversationName}_${System.currentTimeMillis()}.${exportParams.exportFormat.extension}")

        logDialog(translation.format("exporting_message", "conversation" to conversationName))

        val conversationExporter = ConversationExporter(
            context = context,
            friendFeedEntry = feedEntry,
            conversationParticipants = conversationParticipants,
            exportParams = exportParams,
            cacheFolder = publicFolder.resolve("cache").also { if (!it.exists()) it.mkdirs() },
            outputFile = outputFile,
        ).apply { init(); printLog = {
            logDialog(it.toString())
        } }

        var foundMessageCount = 0

        var lastMessageId = fetchMessagesPaginated(conversationId, Long.MAX_VALUE, amount = 1).firstOrNull()?.messageDescriptor?.messageId ?: run {
            logDialog(translation["no_messages_found"])
            return
        }

        while (true) {
            val fetchedMessages = fetchMessagesPaginated(conversationId, lastMessageId, amount = 500).toMutableList()
            if (fetchedMessages.isEmpty()) break

            fetchedMessages.firstOrNull()?.let {
                lastMessageId = it.messageDescriptor!!.messageId!!
            }

            exportParams.messageTypeFilter?.let { filter ->
                fetchedMessages.removeIf { message ->
                    !filter.contains(message.messageContent?.contentType ?: return@removeIf false)
                }
            }

            foundMessageCount += fetchedMessages.size

            if (exportParams.amountOfMessages != null && foundMessageCount >= exportParams.amountOfMessages) {
                fetchedMessages.reversed().subList(0, exportParams.amountOfMessages - (foundMessageCount - fetchedMessages.size)).forEach { message ->
                    conversationExporter.readMessage(message)
                }
                break
            }

            fetchedMessages.reversed().forEach { message ->
                conversationExporter.readMessage(message)
            }

            setStatus("Exporting (found ${foundMessageCount})")
        }

        if (exportParams.exportFormat == ExportFormat.HTML) conversationExporter.awaitDownload()
        conversationExporter.close()
        logDialog(translation["writing_output"])
        dialogLogs.clear()
        logDialog("\n" + translation.format("exported_to",
            "path" to outputFile.absolutePath.toString()
        ) + "\n")
    }
}