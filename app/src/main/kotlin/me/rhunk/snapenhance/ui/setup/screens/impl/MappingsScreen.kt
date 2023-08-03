package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen

class MappingsScreen : SetupScreen() {
    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val infoText = remember { mutableStateOf(null as String?) }
        val isGenerating = remember { mutableStateOf(false) }

        if (infoText.value != null) {
            Dialog(onDismissRequest = {
                infoText.value = null
            }) {
                Surface(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = infoText.value!!)
                        Button(onClick = {
                            infoText.value = null
                        },
                        modifier = Modifier.padding(top = 5.dp).align(alignment = androidx.compose.ui.Alignment.End)) {
                            Text(text = "OK")
                        }
                    }
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

        val hasMappings = remember { mutableStateOf(false) }

        DialogText(text = context.translation["setup.mappings.dialog"])
        if (hasMappings.value) return
        Button(onClick = {
            if (isGenerating.value) return@Button
            isGenerating.value = true
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    tryToGenerateMappings()
                    allowNext(true)
                    infoText.value = context.translation["setup.mappings.generate_success"]
                    hasMappings.value = true
                }.onFailure {
                    isGenerating.value = false
                    infoText.value = context.translation["setup.mappings.generate_failure"] + "\n\n" + it.message
                    Logger.error("Failed to generate mappings", it)
                }
            }
        }) {
            if (isGenerating.value) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 5.dp).size(25.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colors.onPrimary
                )
            } else {
                Text(text = context.translation["setup.mappings.generate_button"])
            }
        }
    }
}