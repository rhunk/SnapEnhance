package me.rhunk.snapenhance.ui.util

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rhunk.snapenhance.core.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.config.DataProcessors
import me.rhunk.snapenhance.core.config.PropertyPair


class AlertDialogs(
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
    fun ConfirmDialog(
        title: String,
        message: String? = null,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DefaultDialogCard {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 5.dp, bottom = 10.dp)
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { onDismiss() }) {
                    Text(text = translation["button.cancel"])
                }
                Button(onClick = { onConfirm() }) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun InfoDialog(
        title: String,
        message: String? = null,
        onDismiss: () -> Unit,
    ) {
        DefaultDialogCard {
            Text(
                text = title,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 5.dp, bottom = 10.dp)
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { onDismiss() }) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun TranslatedText(property: PropertyPair<*>, key: String, modifier: Modifier = Modifier) {
        Text(
            text = property.key.propertyOption(translation, key),
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

        var selectedValue by remember {
            mutableStateOf(property.value.getNullable()?.toString() ?: "null")
        }

        DefaultDialogCard {
            keys.forEachIndexed { index, item ->
                fun select() {
                    selectedValue = item
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
                        selected = selectedValue == item,
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
                    Text(text = translation["button.cancel"])
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
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun RawInputDialog(onDismiss: () -> Unit, onConfirm: (value: String) -> Unit) {
        val focusRequester = remember { FocusRequester() }

        DefaultDialogCard {
            val fieldValue = remember {
                mutableStateOf(TextFieldValue())
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
                singleLine = true
            )

            Row(
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { onDismiss() }) {
                    Text(text = translation["button.cancel"])
                }
                Button(onClick = {
                    onConfirm(fieldValue.value.text)
                }) {
                    Text(text = translation["button.ok"])
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
                var state by remember { mutableStateOf(toggledStates.contains(key)) }

                fun toggle(value: Boolean? = null) {
                    state = value ?: !state
                    if (state) {
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
                        checked = state,
                        onCheckedChange = {
                            toggle(it)
                        }
                    )
                }
            }
        }
    }
}
