package me.rhunk.snapenhance.common.scripting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.ui.components.Node
import me.rhunk.snapenhance.common.scripting.ui.components.NodeType
import me.rhunk.snapenhance.common.scripting.ui.components.impl.ActionNode
import me.rhunk.snapenhance.common.scripting.ui.components.impl.ActionType
import kotlin.math.abs


@Composable
@Suppress("UNCHECKED_CAST")
private fun DrawNode(node: Node) {
    val coroutineScope = rememberCoroutineScope()
    val cachedAttributes = remember { mutableStateMapOf(*node.attributes.toList().toTypedArray()) }

    node.uiChangeDetection = { key, value ->
        coroutineScope.launch {
            cachedAttributes[key] = value
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            node.uiChangeDetection = { _, _ -> }
        }
    }

    val arrangement = cachedAttributes["arrangement"]
    val alignment = cachedAttributes["alignment"]
    val spacing = cachedAttributes["spacing"]?.toString()?.toInt()?.let { abs(it) }

    val rowColumnModifier = Modifier
        .then(if (cachedAttributes["fillMaxWidth"] as? Boolean == true) Modifier.fillMaxWidth() else Modifier)
        .then(if (cachedAttributes["fillMaxHeight"] as? Boolean == true) Modifier.fillMaxHeight() else Modifier)
        .padding(
            (cachedAttributes["padding"]
                ?.toString()
                ?.toInt()
                ?.let { abs(it) } ?: 2).dp)

    fun runCallbackSafe(callback: () -> Unit) {
        runCatching {
            callback()
        }.onFailure {
            AbstractLogger.directError("Error running callback", it)
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
        NodeType.ACTION -> {
            when ((node as ActionNode).actionType) {
                ActionType.LAUNCHED -> {
                    LaunchedEffect(node.key) {
                        runCallbackSafe {
                            node.callback()
                        }
                    }
                }
                ActionType.DISPOSE -> {
                    DisposableEffect(Unit) {
                        onDispose {
                            runCallbackSafe {
                                node.callback()
                            }
                        }
                    }
                }
            }
        }
        NodeType.COLUMN -> {
            Column(
                verticalArrangement = arrangement as? Arrangement.Vertical ?: spacing?.let { Arrangement.spacedBy(it.dp) } ?: Arrangement.Top,
                horizontalAlignment = alignment as? Alignment.Horizontal ?: Alignment.Start,
                modifier = rowColumnModifier
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
                modifier = rowColumnModifier
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
fun ScriptInterface(interfaceBuilder: InterfaceBuilder) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        interfaceBuilder.nodes.forEach { node ->
            DrawNode(node)
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching {
                    interfaceBuilder.onDisposeCallback?.invoke()
                }.onFailure {
                    AbstractLogger.directError("Error running onDisposed callback", it)
                }
            }
        }
    }
}