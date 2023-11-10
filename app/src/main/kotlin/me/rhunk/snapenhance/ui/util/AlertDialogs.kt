package me.rhunk.snapenhance.ui.util

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.common.config.DataProcessors
import me.rhunk.snapenhance.common.config.PropertyPair
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay


class AlertDialogs(
    private val translation: LocaleWrapper,
){
    @Composable
    fun DefaultDialogCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(10.dp, 5.dp, 10.dp, 10.dp)
                .then(modifier),
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
            var fieldValue by remember {
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
                value = fieldValue,
                onValueChange = { fieldValue = it },
                keyboardOptions = when (property.key.dataType.type) {
                    DataProcessors.Type.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    DataProcessors.Type.FLOAT -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    else -> KeyboardOptions(keyboardType = KeyboardType.Text)
                },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { dismiss() }) {
                    Text(text = translation["button.cancel"])
                }
                Button(onClick = {
                    if (fieldValue.text.isNotEmpty() && property.key.params.inputCheck?.invoke(fieldValue.text) == false) {
                        dismiss()
                        return@Button
                    }

                    when (property.key.dataType.type) {
                        DataProcessors.Type.INTEGER -> {
                            runCatching {
                                property.value.setAny(fieldValue.text.toInt())
                            }.onFailure {
                                property.value.setAny(0)
                            }
                        }
                        DataProcessors.Type.FLOAT -> {
                            runCatching {
                                property.value.setAny(fieldValue.text.toFloat())
                            }.onFailure {
                                property.value.setAny(0f)
                            }
                        }
                        else -> property.value.setAny(fieldValue.text)
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
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
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

    @Composable
    fun ChooseLocationDialog(property: PropertyPair<*>, dismiss: () -> Unit = {}) {
        val coordinates = remember {
            (property.value.get() as Pair<*, *>).let {
                it.first.toString().toDouble() to it.second.toString().toDouble()
            }
        }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }

        var marker by remember { mutableStateOf<Marker?>(null) }
        val mapView = remember {
            MapView(context).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setTileSource(TileSourceFactory.MAPNIK)

                val startPoint = GeoPoint(coordinates.first, coordinates.second)
                controller.setZoom(10.0)
                controller.setCenter(startPoint)

                marker = Marker(this).apply {
                    isDraggable = true
                    position = startPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }

                overlays.add(object: Overlay() {
                    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                        marker?.position = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                        mapView.invalidate()
                        return true
                    }
                })

                overlays.add(marker)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                mapView.onDetach()
            }
        }

        var customCoordinatesDialog by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.9f),
        ) {
            AndroidView(
                factory = { mapView }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        val lat = marker?.position?.latitude ?: coordinates.first
                        val lon = marker?.position?.longitude ?: coordinates.second
                        property.value.setAny(lat to lon)
                        dismiss()
                    }) {
                    Icon(
                        modifier = Modifier
                            .size(60.dp)
                            .padding(5.dp),
                        imageVector = Icons.Filled.Check,
                        contentDescription = null
                    )
                }

                FilledIconButton(
                    onClick = {
                        customCoordinatesDialog = true
                    }) {
                    Icon(
                        modifier = Modifier
                            .size(60.dp)
                            .padding(5.dp),
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null
                    )
                }
            }

            if (customCoordinatesDialog) {
                val lat = remember { mutableStateOf(coordinates.first.toString()) }
                val lon = remember { mutableStateOf(coordinates.second.toString()) }

                DefaultDialogCard(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 10.dp),
                        value = lat.value,
                        onValueChange = { lat.value = it },
                        label = { Text(text = "Latitude") },
                        singleLine = true
                    )
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 10.dp),
                        value = lon.value,
                        onValueChange = { lon.value = it },
                        label = { Text(text = "Longitude") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(onClick = {
                            customCoordinatesDialog = false
                        }) {
                            Text(text = translation["button.cancel"])
                        }

                        Button(onClick = {
                            marker?.position = GeoPoint(lat.value.toDouble(), lon.value.toDouble())
                            mapView.controller.setCenter(marker?.position)
                            mapView.invalidate()
                            customCoordinatesDialog = false
                        }) {
                            Text(text = translation["button.ok"])
                        }
                    }
                }
            }
        }
    }
}
