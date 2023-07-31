package me.rhunk.snapenhance.manager.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHost
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.manager.Dialogs
import me.rhunk.snapenhance.manager.Section

typealias ClickCallback = (Boolean) -> Unit
typealias RegisterClickCallback = (ClickCallback) -> ClickCallback

class FeaturesSection : Section() {
    private val dialogs by lazy { Dialogs(manager) }

    @Composable
    private fun PropertyAction(item: ConfigProperty, registerClickCallback: RegisterClickCallback) {
        val showDialog = remember { mutableStateOf(false) }
        val dialogComposable = remember { mutableStateOf<@Composable () -> Unit>({})}

        fun registerDialogOnClickCallback() = registerClickCallback {
            showDialog.value = true
        }

        if (showDialog.value) {
            Dialog(onDismissRequest = { showDialog.value = false }, properties = DialogProperties()) {
                dialogComposable.value()
            }
        }

        when (val container = remember { item.valueContainer }) {
            is ConfigStateValue -> {
                val state = remember { mutableStateOf(container.value()) }
                Switch(
                    checked = state.value,
                    onCheckedChange = registerClickCallback {
                        state.value = state.value.not()
                        container.writeFrom(state.value.toString())
                    }
                )
            }

            is ConfigStateSelection -> {
                registerDialogOnClickCallback()
                dialogComposable.value = {
                    dialogs.StateSelectionDialog(item)
                }
                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp),
                    text = if (item.disableValueLocalization) container.value() else {
                        manager.translation.propertyOption(item, container.value())
                    },
                )
            }

            is ConfigStateListValue, is ConfigStringValue, is ConfigIntegerValue -> {
                dialogComposable.value = {
                    when (container) {
                        is ConfigStateListValue -> {
                            dialogs.StateListDialog(item)
                        }
                        is ConfigStringValue, is ConfigIntegerValue -> {
                            dialogs.KeyboardInputDialog(item) { showDialog.value = false }
                        }
                    }
                }

                registerDialogOnClickCallback().let { { it.invoke(true) } }.also {
                    if (container is ConfigIntegerValue) {
                        FilledIconButton(onClick = it) {
                            Text(text = container.value().toString(), modifier = Modifier.wrapContentWidth(), overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PropertyCard(item: ConfigProperty) {
        val clickCallback = remember { mutableStateOf<ClickCallback?>(null) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clickCallback.value?.invoke(true)
                }
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = true)
                        .padding(all = 10.dp)
                ) {
                    Text(text = manager.translation.propertyName(item), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = manager.translation.propertyDescription(item),
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(all = 10.dp)
                ) {
                    PropertyAction(item, registerClickCallback = { callback ->
                        clickCallback.value = callback
                        callback
                    })
                }
            }
        }
    }

    @Composable
    private fun PropertyContainer() {
        val properties = remember {
            val items by manager.config
            items.properties.map { it.key to it.value }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            items(properties) { (key, value) ->
               // Logger.debug("key: $key, value: $value")
            }
        }
    }


    @Composable
    @Preview
    override fun Content() {
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberScaffoldState()
        Scaffold(
            snackbarHost = { SnackbarHost(scaffoldState.snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        //manager.config.writeConfig()
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar("Saved")
                        }
                    },
                    containerColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            content = { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Text(
                        text = "Features",
                        modifier = Modifier.padding(all = 10.dp),
                        fontSize = 20.sp
                    )
                    PropertyContainer()
                }
            }
        )
    }
}