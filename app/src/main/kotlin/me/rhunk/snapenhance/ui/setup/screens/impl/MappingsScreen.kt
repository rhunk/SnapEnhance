package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import me.rhunk.snapenhance.ui.util.AlertDialogs

class MappingsScreen : SetupScreen() {
    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        var infoText by remember { mutableStateOf(null as String?) }
        var isGenerating by remember { mutableStateOf(false) }

        if (infoText != null) {
            Dialog(onDismissRequest = {
                infoText = null
            }) {
                remember { AlertDialogs(context.translation) }.InfoDialog(title = infoText!!) {
                    infoText = null
                }
            }
        }

        fun tryToGenerateMappings() {
            //check for snapchat installation
            val installationSummary = context.getInstallationSummary()
            if (installationSummary.snapchatInfo == null) {
                throw Exception(context.translation["setup.mappings.generate_failure_no_snapchat"])
            }
            with(context.mappings) {
                refresh()
            }
        }

        var hasMappings by remember { mutableStateOf(false) }

        DialogText(text = context.translation["setup.mappings.dialog"])
        if (hasMappings) return
        Button(onClick = {
            if (isGenerating) return@Button
            isGenerating = true
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    tryToGenerateMappings()
                    allowNext(true)
                    infoText = context.translation["setup.mappings.generate_success"]
                    hasMappings = true
                }.onFailure {
                    isGenerating = false
                    infoText = context.translation["setup.mappings.generate_failure"] + "\n\n" + it.message
                    Logger.error("Failed to generate mappings", it)
                }
            }
        }) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding()
                        .size(30.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text = context.translation["setup.mappings.generate_button"])
            }
        }
    }
}