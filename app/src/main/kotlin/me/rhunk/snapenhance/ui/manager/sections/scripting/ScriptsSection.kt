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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.scripting.impl.ui.components.Node
import me.rhunk.snapenhance.scripting.impl.ui.components.NodeType
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import me.rhunk.snapenhance.ui.manager.Section
import kotlin.math.abs

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
    private fun DrawNode(node: Node) {
        val coroutineScope = rememberCoroutineScope()
        val cachedAttributes = remember { mutableStateMapOf(*node.attributes.toList().toTypedArray()) }

        node.uiChangeDetection = { key, value ->
            coroutineScope.launch {
                cachedAttributes[key] = value
            }
        }

        val padding = cachedAttributes["padding"]?.toString()?.toInt()?.let { abs(it) } ?: 2
        val arrangement = cachedAttributes["arrangement"]
        val alignment = cachedAttributes["alignment"]
        val spacing = cachedAttributes["spacing"]?.toString()?.toInt()?.let { abs(it) }

        fun runCallbackSafe(callback: () -> Unit) {
            runCatching {
                callback()
            }.onFailure {
                context.log.error("Error running callback", it)
            }
        }

        @Composable
        fun NodeLabel() {
            Text(
                text = cachedAttributes["label"] as String,
                fontSize = (cachedAttributes["fontSize"]?.toString()?.toInt() ?: 14).sp,
                color = (cachedAttributes["color"] as? Long)?.let { Color(it) } ?: Color.Unspecified
            )
        }

        when (node.type) {
            NodeType.COLUMN -> {
                Column(
                    verticalArrangement = arrangement as? Arrangement.Vertical ?: spacing?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Top,
                    horizontalAlignment = alignment as? Alignment.Horizontal ?: Alignment.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding.dp)
                ) {
                    node.children.forEach { child ->
                        DrawNode(child)
                    }
                }
            }
            NodeType.ROW -> {
                Row(
                    horizontalArrangement = arrangement as? Arrangement.Horizontal ?: spacing?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.SpaceBetween,
                    verticalAlignment = alignment as? Alignment.Vertical ?: Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding.dp)
                ) {
                    node.children.forEach { child ->
                        DrawNode(child)
                    }
                }
            }
            NodeType.TEXT -> NodeLabel()
            NodeType.SWITCH -> {
                var switchState by remember {
                    mutableStateOf(cachedAttributes["state"] as Boolean)
                }
                Switch(
                    checked = switchState,
                    onCheckedChange = { state ->
                        runCallbackSafe {
                            switchState = state
                            node.setAttribute("state", state)
                            (cachedAttributes["callback"] as? (Boolean) -> Unit)?.let { it(state) }
                        }
                    }
                )
            }
            NodeType.SLIDER -> {
                var sliderValue by remember {
                    mutableFloatStateOf((cachedAttributes["value"] as Int).toFloat())
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        runCallbackSafe {
                            sliderValue = value
                            node.setAttribute("value", value.toInt())
                            (cachedAttributes["callback"] as? (Int) -> Unit)?.let { it(value.toInt()) }
                        }
                    },
                    valueRange = (cachedAttributes["min"] as Int).toFloat()..(cachedAttributes["max"] as Int).toFloat(),
                    steps = cachedAttributes["step"] as Int,
                )
            }
            NodeType.BUTTON -> {
                OutlinedButton(onClick = {
                    runCallbackSafe {
                        (cachedAttributes["callback"] as? () -> Unit)?.let { it() }
                    }
                }) {
                    NodeLabel()
                }
            }
            else -> {}
        }
    }

    @Composable
    fun ScriptSettings(script: ModuleInfo) {
        val settingsInterface = remember {
            context.scriptManager.getScriptInterface(script.name, "settings")
        } ?: run {
            Text(
                text = "This module does not have any settings",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            settingsInterface.nodes.forEach { node ->
                DrawNode(node)
            }

            LaunchedEffect(settingsInterface) {
                settingsInterface.onLaunchedCallback?.invoke()
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