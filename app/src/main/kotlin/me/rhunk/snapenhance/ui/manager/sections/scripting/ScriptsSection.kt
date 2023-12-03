package me.rhunk.snapenhance.ui.manager.sections.scripting

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.scripting.impl.ui.InterfaceManager
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.chooseFolder
import me.rhunk.snapenhance.ui.util.pullrefresh.PullRefreshIndicator
import me.rhunk.snapenhance.ui.util.pullrefresh.pullRefresh
import me.rhunk.snapenhance.ui.util.pullrefresh.rememberPullRefreshState

class ScriptsSection : Section() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

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
                    Text(text = script.name, fontSize = 20.sp,)
                    Text(text = script.description ?: "No description", fontSize = 14.sp,)
                }
                IconButton(onClick = { openSettings = !openSettings }) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings",)
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
    override fun FloatingActionButton() {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = {

                },
                icon= { Icon(imageVector = Icons.Default.Link, contentDescription = "Link") },
                text = {
                    Text(text = "Import from URL")
                },
            )
            ExtendedFloatingActionButton(
                onClick = {
                    context.scriptManager.getScriptsFolder()?.let {
                        context.androidContext.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                data = it.uri
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                },
                icon= { Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Folder") },
                text = {
                    Text(text = "Open Scripts Folder")
                },
            )
        }
    }


    @Composable
    fun ScriptSettings(script: ModuleInfo) {
        var settingsError by remember {
            mutableStateOf(null as Throwable?)
        }

        val settingsInterface = remember {
            val module = context.scriptManager.runtime.getModuleByName(script.name) ?: return@remember null
            runCatching {
                (module.extras["im"] as? InterfaceManager)?.buildInterface("settings")
            }.onFailure {
                settingsError = it
            }.getOrNull()
        }

        if (settingsInterface == null) {
            Text(
                text = settingsError?.message ?: "This module does not have any settings",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        } else  {
            ScriptInterface(interfaceBuilder = settingsInterface)
        }
    }

    @Composable
    override fun Content() {
        var scriptModules by remember { mutableStateOf(listOf<ModuleInfo>()) }
        var scriptingFolder by remember { mutableStateOf(null as DocumentFile?) }
        val coroutineScope = rememberCoroutineScope()

        var refreshing by remember {
            mutableStateOf(false)
        }

        fun syncScripts() {
            runCatching {
                scriptingFolder = context.scriptManager.getScriptsFolder()
                context.scriptManager.sync()
                scriptModules = context.modDatabase.getScripts()
            }.onFailure {
                context.log.error("Failed to sync scripts", it)
            }
        }

        LaunchedEffect(Unit) {
            syncScripts()
        }

        val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
            refreshing = true
            syncScripts()
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
                    if (scriptingFolder == null) {
                        Text(
                            text = "No scripts folder selected",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            activityLauncherHelper.chooseFolder {
                                context.config.root.scripting.moduleFolder.set(it)
                                context.config.writeConfig()
                                coroutineScope.launch {
                                    syncScripts()
                                }
                            }
                        }) {
                            Text(text = "Select folder")
                        }
                    } else if (scriptModules.isEmpty()) {
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