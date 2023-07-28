package me.rhunk.snapenhance.manager.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.manager.Section

typealias ClickCallback = (Boolean) -> Unit
typealias AddClickCallback = (ClickCallback) -> ClickCallback

class FeaturesSection : Section() {
    @Composable
    private fun PropertyAction(item: ConfigProperty, clickCallback: AddClickCallback) {
        when (val configValueContainer = remember { item.valueContainer }) {
            is ConfigStateValue -> {
                val state = remember {
                    mutableStateOf(configValueContainer.value())
                }

                Switch(
                    checked = state.value,
                    onCheckedChange = clickCallback {
                        state.value = !state.value
                        configValueContainer.writeFrom(state.value.toString())
                    }
                )
            }

            is ConfigStateSelection -> {
                Text(
                    text = configValueContainer.value().let {
                        it.substring(0, it.length.coerceAtMost(20))
                    }
                )
            }

            is ConfigStateListValue -> {
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                }
            }

            is ConfigIntegerValue -> {
                FilledIconButton(onClick = { }) {
                    Text(text = configValueContainer.value().toString())
                }
            }

            is ConfigStringValue -> {
                Text(
                    text = configValueContainer.value().let {
                        it.substring(0, it.length.coerceAtMost(20))
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 10.dp),
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
                    PropertyAction(item, clickCallback = { callback ->
                        clickCallback.value = callback
                        callback
                    })
                }
            }
        }
    }


    @Composable
    @Preview
    override fun Content() {
        val configItems = remember {
            ConfigProperty.sortedByCategory()
        }
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberScaffoldState()
        Scaffold(
            snackbarHost = { SnackbarHost(scaffoldState.snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        manager.config.save()
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar("Saved")
                        }
                    },
                    containerColor = MaterialTheme.colors.primary,
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        items(configItems) { item ->
                            PropertyCard(item)
                        }
                    }
                }
            }
        )
    }
}