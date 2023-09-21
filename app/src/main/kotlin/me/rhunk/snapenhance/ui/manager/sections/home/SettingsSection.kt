package me.rhunk.snapenhance.ui.manager.sections.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.action.EnumAction
import me.rhunk.snapenhance.core.bridge.types.BridgeFileType
import me.rhunk.snapenhance.manager.impl.ActionManager
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.AlertDialogs

class SettingsSection : Section() {
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

    private fun launchActionIntent(action: EnumAction) {
        val intent = context.androidContext.packageManager.getLaunchIntentForPackage(Constants.SNAPCHAT_PACKAGE_NAME)
        intent?.putExtra(ActionManager.ACTION_PARAMETER, action.key)
        context.androidContext.startActivity(intent)
    }

    @Composable
    override fun Content() {
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