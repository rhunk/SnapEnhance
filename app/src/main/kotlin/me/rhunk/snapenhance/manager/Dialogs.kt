package me.rhunk.snapenhance.manager

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.manager.data.ManagerContext


class Dialogs(
    private val context: ManagerContext
) {
    @Composable
    fun DefaultDialogCard(content: @Composable ColumnScope.() -> Unit) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(10.dp, 5.dp, 10.dp, 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp, 10.dp, 10.dp, 10.dp)
                    .verticalScroll(ScrollState(0)),
            ) { content() }
        }
    }

    @Composable
    fun DefaultEntryText(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            modifier = Modifier
                .padding(10.dp, 10.dp, 10.dp, 10.dp)
                .then(modifier)
        )
    }

    @Composable
    fun StateSelectionDialog(config: ConfigProperty) {
        assert(config.valueContainer is ConfigStateSelection)
        val keys = (config.valueContainer as ConfigStateSelection).keys()
        val selectedValue = remember {
            mutableStateOf(config.valueContainer.value())
        }
        DefaultDialogCard {
            keys.forEach { item ->
                fun select() {
                    selectedValue.value = item
                    config.valueContainer.writeFrom(item)
                }

                Row(
                    modifier = Modifier.clickable { select() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DefaultEntryText(
                        text = if (config.disableValueLocalization)
                            item
                        else context.translation.propertyOption(config, item),
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selectedValue.value == item,
                        onClick = { select() }
                    )
                }
            }
        }
    }

    @Composable
    fun KeyboardInputDialog(config: ConfigProperty, dismiss: () -> Unit = {}) {
        val focusRequester = remember { FocusRequester() }

        DefaultDialogCard {
            val fieldValue = remember {
                mutableStateOf(config.valueContainer.read().let {
                    TextFieldValue(
                        text = it,
                        selection = TextRange(it.length)
                    )
                })
            }

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp)
                    .onGloballyPositioned {
                        focusRequester.requestFocus()
                    }
                    .focusRequester(focusRequester),
                value = fieldValue.value,
                onValueChange = {
                    fieldValue.value = it
                },
                keyboardOptions = when (config.valueContainer) {
                    is ConfigIntegerValue -> {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    }
                    else -> {
                        KeyboardOptions(keyboardType = KeyboardType.Text)
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { dismiss() }) {
                    Text(text = "Cancel")
                }
                Button(onClick = {
                    config.valueContainer.writeFrom(fieldValue.value.text)
                    dismiss()
                }) {
                    Text(text = "Ok")
                }
            }
        }
    }

    @Composable
    fun StateListDialog(config: ConfigProperty) {
        assert(config.valueContainer is ConfigStateListValue)
        val stateList = (config.valueContainer as ConfigStateListValue).value()
        DefaultDialogCard {
            stateList.keys.forEach { key ->
                val state = remember {
                    mutableStateOf(stateList[key] ?: false)
                }

                fun toggle(value: Boolean? = null) {
                    state.value = value ?: !state.value
                    stateList[key] = state.value
                }

                Row(
                    modifier = Modifier.clickable { toggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DefaultEntryText(
                        text = if (config.disableValueLocalization)
                            key
                        else context.translation.propertyOption(config, key),
                        modifier = Modifier
                            .weight(1f)
                    )
                    Switch(
                        checked = state.value,
                        onCheckedChange = {
                            toggle(it)
                        }
                    )
                }
            }
        }
    }
}
