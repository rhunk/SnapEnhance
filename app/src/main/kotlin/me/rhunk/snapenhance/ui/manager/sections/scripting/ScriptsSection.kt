package me.rhunk.snapenhance.ui.manager.sections.scripting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import me.rhunk.snapenhance.ui.manager.Section

class ScriptsSection : Section() {
    @Composable
    fun ModuleItem(script: ModuleInfo) {
        var enabled by remember {
            mutableStateOf(context.modDatabase.isScriptEnabled(script.name))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = script.name,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = script.description ?: "No description",
                        fontSize = 14.sp,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        context.modDatabase.setScriptEnabled(script.name, it)
                        enabled = it
                    }
                )
            }
        }
    }


    @Composable
    override fun Content() {
        val scriptModules = remember {
            context.modDatabase.getScripts()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                if (scriptModules.isEmpty()) {
                    Text(
                        text = "No scripts found",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(scriptModules.size) { index ->
                ModuleItem(scriptModules[index])
            }
        }
    }
}