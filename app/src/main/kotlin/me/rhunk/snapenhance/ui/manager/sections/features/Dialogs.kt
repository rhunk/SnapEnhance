package me.rhunk.snapenhance.ui.manager.sections.features

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
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.config.DataProcessors
import me.rhunk.snapenhance.core.config.PropertyPair


class Dialogs(
    private val translation: LocaleWrapper,
){
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
    fun TranslatedText(property: PropertyPair<*>, key: String, modifier: Modifier = Modifier) {
        Text(
            text = when (key) {
                "null" -> translation["manager.features.disabled"]
                else -> if (property.key.params.shouldTranslate) translation["features.options.${property.key.name}.$key"] else key
            },
            modifier = Modifier
                .padding(10.dp, 10.dp, 10.dp, 10.dp)
                .then(modifier)
        )
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun UniqueSelectionDialog(property: PropertyPair<*>) {
        val keys = (property.value.defaultValues as List<String>).toMutableList().apply {
            add(0, "null")
        }

        val selectedValue = remember {
            mutableStateOf(property.value.getNullable()?.toString() ?: "null")
        }

        DefaultDialogCard {
            keys.forEachIndexed { index, item ->
                fun select() {
                    selectedValue.value = item
                    property.value.setAny(if (index == 0) {
                        null
                    } else {
                        item
                    })
                }

                Row(
                    modifier = Modifier.clickable { select() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TranslatedText(
                        property = property,
                        key = item,
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
    fun KeyboardInputDialog(property: PropertyPair<*>, dismiss: () -> Unit = {}) {
        val focusRequester = remember { FocusRequester() }

        DefaultDialogCard {
            val fieldValue = remember {
                mutableStateOf(property.value.get().toString().let {
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
                keyboardOptions = when (property.key.dataType.type) {
                    DataProcessors.Type.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    DataProcessors.Type.FLOAT -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    else -> KeyboardOptions(keyboardType = KeyboardType.Text)
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
                    when (property.key.dataType.type) {
                        DataProcessors.Type.INTEGER -> {
                            runCatching {
                                property.value.setAny(fieldValue.value.text.toInt())
                            }.onFailure {
                                property.value.setAny(0)
                            }
                        }
                        DataProcessors.Type.FLOAT -> {
                            runCatching {
                                property.value.setAny(fieldValue.value.text.toFloat())
                            }.onFailure {
                                property.value.setAny(0f)
                            }
                        }
                        else -> property.value.setAny(fieldValue.value.text)
                    }
                    dismiss()
                }) {
                    Text(text = "Ok")
                }
            }
        }
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun MultipleSelectionDialog(property: PropertyPair<*>) {
        val defaultItems = property.value.defaultValues as List<String>
        val toggledStates = property.value.get() as MutableList<String>
        DefaultDialogCard {
            defaultItems.forEach { key ->
                val state = remember {
                    mutableStateOf(toggledStates.contains(key))
                }

                fun toggle(value: Boolean? = null) {
                    state.value = value ?: !state.value
                    if (state.value) {
                        toggledStates.add(key)
                    } else {
                        toggledStates.remove(key)
                    }
                }

                Row(
                    modifier = Modifier.clickable { toggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TranslatedText(
                        property = property,
                        key = key,
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
