package me.rhunk.snapenhance.ui.manager.sections.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.saveFile

class SettingsSection(
    private val activityLauncherHelper: ActivityLauncherHelper
) : Section() {
    private val dialogs by lazy { AlertDialogs(context.translation) }

    @Composable
    private fun RowTitle(title: String) {
        Text(text = title, modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }

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

        ShiftedRow(
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

    private fun launchActionIntent(action: EnumAction) {
        val intent = context.androidContext.packageManager.getLaunchIntentForPackage(Constants.SNAPCHAT_PACKAGE_NAME)
        intent?.putExtra(EnumAction.ACTION_PARAMETER, action.key)
        context.androidContext.startActivity(intent)
    }

    @Composable
    private fun ShiftedRow(
        modifier: Modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        verticalAlignment: Alignment.Vertical = Alignment.Top,
        content: @Composable RowScope.() -> Unit
    ) {
        Row(
            modifier = modifier.padding(start = 26.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) { content(this) }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(ScrollState(0))
        ) {
            RowTitle(title = "Actions")
            EnumAction.entries.forEach { enumAction ->
                RowAction(title = context.translation["actions.${enumAction.key}"]) {
                    launchActionIntent(enumAction)
                }
            }
            RowTitle(title = "Message Logger")
            ShiftedRow {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var storedMessagesCount by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            storedMessagesCount = context.messageLogger.getStoredMessageCount()
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Text(text = "$storedMessagesCount messages", modifier = Modifier.weight(1f))
                        Button(onClick = {
                            runCatching {
                                activityLauncherHelper.saveFile("message_logger.db", "application/octet-stream") { uri ->
                                    context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { outputStream ->
                                        context.messageLogger.databaseFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                            }.onFailure {
                                context.log.error("Failed to export database", it)
                                context.longToast("Failed to export database! ${it.localizedMessage}")
                            }
                        }) {
                            Text(text = "Export")
                        }
                        Button(onClick = {
                            runCatching {
                                context.messageLogger.clearMessages()
                                storedMessagesCount = 0
                            }.onFailure {
                                context.log.error("Failed to clear messages", it)
                                context.longToast("Failed to clear messages! ${it.localizedMessage}")
                            }.onSuccess {
                                context.shortToast("Done!")
                            }
                        }) {
                            Text(text = "Clear")
                        }
                    }
                }
            }

            RowTitle(title = "Clear App Files")
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var selectedFileType by remember { mutableStateOf(BridgeFileType.entries.first()) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 26.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        TextField(
                            value = selectedFileType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor()
                        )
                        
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            BridgeFileType.entries.forEach { fileType ->
                                DropdownMenuItem(onClick = {
                                    expanded = false
                                    selectedFileType = fileType
                                }, text = {
                                    Text(text = fileType.displayName)
                                })
                            }
                        }
                    }
                }
                Button(onClick = {
                    runCatching {
                        selectedFileType.resolve(context.androidContext).delete()
                    }.onFailure {
                        context.log.error("Failed to clear file", it)
                        context.longToast("Failed to clear file! ${it.localizedMessage}")
                    }.onSuccess {
                        context.shortToast("Done!")
                    }
                }) {
                    Text(text = "Clear")
                }
            }
        }
    }
}