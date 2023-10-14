package me.rhunk.snapenhance.ui.manager.sections.scripting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.scripting.impl.ui.InterfaceManager
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.pullrefresh.PullRefreshIndicator
import me.rhunk.snapenhance.ui.util.pullrefresh.pullRefresh
import me.rhunk.snapenhance.ui.util.pullrefresh.rememberPullRefreshState

class ScriptsSection : Section() {
    @Composable
    fun ModuleItem(script: ModuleInfo) {
        var enabled by remember {
            mutableStateOf(context.modDatabase.isScriptEnabled(script.name))
        }
        var openSettings by remember {
            mutableStateOf(false)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            elevation = CardDefaults.cardElevation()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openSettings = !openSettings
                    }
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
                IconButton(onClick = {
                    openSettings = !openSettings
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        context.modDatabase.setScriptEnabled(script.name, it)
                        if (it) {
                            context.scriptManager.loadScript(script.name)
                        }
                        enabled = it
                    }
                )
            }

            if (openSettings) {
                ScriptSettings(script)
            }
        }
    }


    @Composable
    fun ScriptSettings(script: ModuleInfo) {
        val settingsInterface = remember {
            val module = context.scriptManager.runtime.getModuleByName(script.name) ?: return@remember null
            (module.extras["im"] as? InterfaceManager)?.buildInterface("settings")
        } ?: run {
            Text(
                text = "This module does not have any settings",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
            return
        }

        ScriptInterface(interfaceBuilder = settingsInterface)
    }

    @Composable
    override fun Content() {
        var scriptModules by remember {
            mutableStateOf(context.modDatabase.getScripts())
        }
        val coroutineScope = rememberCoroutineScope()

        var refreshing by remember {
            mutableStateOf(false)
        }

        val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
            refreshing = true
            runCatching {
                context.scriptManager.sync()
                scriptModules = context.modDatabase.getScripts()
            }.onFailure {
                context.log.error("Failed to sync scripts", it)
            }
            coroutineScope.launch {
                delay(300)
                refreshing = false
            }
        })

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState),
                horizontalAlignment = Alignment.CenterHorizontally
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

            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}