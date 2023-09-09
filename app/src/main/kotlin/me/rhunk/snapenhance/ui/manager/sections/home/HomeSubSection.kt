package me.rhunk.snapenhance.ui.manager.sections.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.LogChannels
import me.rhunk.snapenhance.core.LogLevel
import me.rhunk.snapenhance.LogReader
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.action.EnumAction
import me.rhunk.snapenhance.core.bridge.types.BridgeFileType
import me.rhunk.snapenhance.manager.impl.ActionManager
import me.rhunk.snapenhance.ui.util.AlertDialogs

class HomeSubSection(
    private val context: RemoteSideContext
) {
    private val dialogs by lazy { AlertDialogs(context.translation) }

    private lateinit var logListState: LazyListState

    @Composable
    private fun RowAction(title: String, requireConfirmation: Boolean = false, action: () -> Unit) {
        var confirmationDialog by remember {
            mutableStateOf(false)
        }

        fun takeAction() {
            if (requireConfirmation) {
                confirmationDialog = true
            } else {
                action()
            }
        }

        if (requireConfirmation && confirmationDialog) {
            Dialog(onDismissRequest = { confirmationDialog = false }) {
                dialogs.ConfirmDialog(title = "Are you sure?", onConfirm = {
                    action()
                    confirmationDialog = false
                }, onDismiss = {
                    confirmationDialog = false
                })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .clickable {
                    takeAction()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, modifier = Modifier.padding(start = 26.dp))
            IconButton(onClick = { takeAction() }) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    fun LogsSection() {
        val coroutineScope = rememberCoroutineScope()
        val clipboardManager = LocalClipboardManager.current
        var lineCount by remember { mutableIntStateOf(0) }
        var logReader by remember { mutableStateOf<LogReader?>(null) }
        logListState = remember { LazyListState(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface )
                    .horizontalScroll(ScrollState(0)),
                state = logListState
            ) {
                items(lineCount) { index ->
                    val line = logReader?.getLogLine(index) ?: return@items
                    var expand by remember { mutableStateOf(false) }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    coroutineScope.launch {
                                        clipboardManager.setText(AnnotatedString(line.message))
                                    }
                                },
                                onTap = {
                                    expand = !expand
                                }
                            )
                        }) {

                        Row(
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 30.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!expand) {
                                Icon(
                                    imageVector = when (line.logLevel) {
                                        LogLevel.DEBUG -> Icons.Outlined.BugReport
                                        LogLevel.ERROR, LogLevel.ASSERT -> Icons.Outlined.Report
                                        LogLevel.INFO, LogLevel.VERBOSE -> Icons.Outlined.Info
                                        LogLevel.WARN -> Icons.Outlined.Warning
                                    },
                                    contentDescription = null,
                                )

                                Text(
                                    text = LogChannels.fromChannel(line.tag)?.shortName ?: line.tag,
                                    modifier = Modifier.padding(start = 4.dp),
                                    fontWeight = FontWeight.Light,
                                    fontSize = 10.sp,
                                )

                                Text(
                                    text = line.dateTime,
                                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                text = line.message.trimIndent(),
                                fontSize = 10.sp,
                                maxLines = if (expand) Int.MAX_VALUE else 6,
                                overflow = if (expand) TextOverflow.Visible else TextOverflow.Ellipsis,
                                softWrap = !expand,
                            )
                        }
                    }
                }
            }

            if (logReader == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            LaunchedEffect(Unit) {
                coroutineScope.launch(Dispatchers.IO) {
                    runCatching {
                        logReader = context.log.newReader {
                            lineCount++
                        }
                        lineCount = logReader!!.lineCount
                    }.onFailure {
                        context.longToast("Failed to read logs!")
                    }
                }
            }
        }
    }

    @Composable
    fun LogsActionButtons() {
        val coroutineScope = rememberCoroutineScope()
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledIconButton(onClick = {
                coroutineScope.launch {
                    logListState.scrollToItem(0)
                }
            }) {
                Icon(Icons.Filled.KeyboardDoubleArrowUp, contentDescription = null)
            }

            FilledIconButton(onClick = {
                coroutineScope.launch {
                    logListState.scrollToItem((logListState.layoutInfo.totalItemsCount - 1).takeIf { it >= 0 } ?: return@launch)
                }
            }) {
                Icon(Icons.Filled.KeyboardDoubleArrowDown, contentDescription = null)
            }
        }
    }

    private fun launchActionIntent(action: EnumAction) {
        val intent = context.androidContext.packageManager.getLaunchIntentForPackage(Constants.SNAPCHAT_PACKAGE_NAME)
        intent?.putExtra(ActionManager.ACTION_PARAMETER, action.key)
        context.androidContext.startActivity(intent)
    }

    @Composable
    private fun RowTitle(title: String) {
        Text(text = title, modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }

    @Composable
    fun DebugSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(ScrollState(0))
        ) {
            RowTitle(title = "Actions")
            EnumAction.values().forEach { enumAction ->
                RowAction(title = context.translation["actions.${enumAction.key}"]) {
                    launchActionIntent(enumAction)
                }
            }

            RowTitle(title = "Clear Files")
            BridgeFileType.values().forEach { fileType ->
                RowAction(title = fileType.displayName, requireConfirmation = true) {
                    runCatching {
                        fileType.resolve(context.androidContext).delete()
                        context.longToast("Deleted ${fileType.displayName}!")
                    }.onFailure {
                        context.longToast("Failed to delete ${fileType.displayName}!")
                    }
                }
            }
        }
    }
}