package me.rhunk.snapenhance.ui.manager.sections.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.LogReader
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.logger.LogChannel
import me.rhunk.snapenhance.common.logger.LogLevel

class HomeSubSection(
    private val context: RemoteSideContext
) {
    private lateinit var logListState: LazyListState

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
                                    text = LogChannel.fromChannel(line.tag)?.shortName ?: line.tag,
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
}