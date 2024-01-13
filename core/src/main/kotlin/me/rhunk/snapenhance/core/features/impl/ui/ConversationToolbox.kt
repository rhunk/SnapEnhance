package me.rhunk.snapenhance.core.features.impl.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import me.rhunk.snapenhance.common.scripting.ui.EnumScriptInterface
import me.rhunk.snapenhance.common.scripting.ui.InterfaceManager
import me.rhunk.snapenhance.common.scripting.ui.ScriptInterface
import me.rhunk.snapenhance.common.ui.createComposeAlertDialog
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.util.ktx.getId


data class ComposableMenu(
    val title: String,
    val filter: (conversationId: String) -> Boolean,
    val composable: @Composable (alertDialog: AlertDialog, conversationId: String) -> Unit,
)

class ConversationToolbox : Feature("Conversation Toolbox", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val composableList = mutableListOf<ComposableMenu>()
    private val expandedComposableCache = mutableStateMapOf<String, Boolean>()

    fun addComposable(title: String, filter: (conversationId: String) -> Boolean = { true }, composable: @Composable (alertDialog: AlertDialog, conversationId: String) -> Unit) {
        composableList.add(
            ComposableMenu(title, filter, composable)
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityCreate() {
        val defaultInputBarId = context.resources.getId("default_input_bar")

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view.id != defaultInputBarId) return@subscribe
            if (composableList.isEmpty()) return@subscribe

            (event.view as ViewGroup).addView(FrameLayout(event.view.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (52 * context.resources.displayMetrics.density).toInt(),
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                setPadding(25, 0, 25, 0)

                addView(TextView(event.view.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    setOnClickListener {
                        openToolbox()
                    }
                    textSize = 21f
                    text = "\uD83E\uDDF0"
                })
            })
        }

        context.scriptRuntime.eachModule {
            val interfaceManager = getBinding(InterfaceManager::class)?.takeIf {
                it.hasInterface(EnumScriptInterface.CONVERSATION_TOOLBOX)
            } ?: return@eachModule
            addComposable("\uD83D\uDCDC ${moduleInfo.displayName}") { alertDialog, conversationId ->
                ScriptInterface(remember {
                    interfaceManager.buildInterface(EnumScriptInterface.CONVERSATION_TOOLBOX, mapOf(
                        "alertDialog" to alertDialog,
                        "conversationId" to conversationId,
                    ))
                } ?: return@addComposable)
            }
        }
    }

    private fun openToolbox() {
        val openedConversationId = context.feature(Messaging::class).openedConversationUUID?.toString() ?: run {
            context.shortToast("You must open a conversation first")
            return
        }

        createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 100.dp,
                        max = LocalConfiguration.current.screenHeightDp * 0.8f.dp
                    )
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Conversation Toolbox", fontSize = 20.sp, modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))

                composableList.reversed().forEach { (title, filter, composable) ->
                    if (!filter(openedConversationId)) return@forEach
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    expandedComposableCache[title] = !(expandedComposableCache[title] ?: false)
                                }
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                imageVector = if (expandedComposableCache[title] == true) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            )
                            Text(title, fontSize = 16.sp, fontStyle = FontStyle.Italic)
                        }
                        if (expandedComposableCache[title] == true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                runCatching {
                                    composable(alertDialog, openedConversationId)
                                }.onFailure { throwable ->
                                    Text("Failed to load composable: ${throwable.message}")
                                    context.log.error("Failed to load composable: ${throwable.message}", throwable)
                                }
                            }
                        }
                    }
                }
            }
        }.show()
    }
}