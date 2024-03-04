package me.rhunk.snapenhance.ui.manager.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.bridge.wrapper.TrackerLog
import me.rhunk.snapenhance.common.data.TrackerEventType
import me.rhunk.snapenhance.common.data.TrackerRule
import me.rhunk.snapenhance.common.data.TrackerRuleAction
import me.rhunk.snapenhance.common.data.TrackerRuleActionParams
import me.rhunk.snapenhance.common.data.TrackerRuleEvent
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset
import java.text.DateFormat


@OptIn(ExperimentalFoundationApi::class)
class FriendTrackerManagerRoot : Routes.Route() {
    enum class FilterType {
        CONVERSATION, USERNAME, EVENT
    }

    private val titles = listOf("Logs", "Rules")
    private var currentPage by mutableIntStateOf(0)

    override val floatingActionButton: @Composable () -> Unit = {
        var showAddRulePopup by remember { mutableStateOf(false) }
        if (currentPage == 1) {
            ExtendedFloatingActionButton(
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Rule") },
                expanded = false,
                text = {},
                onClick = { showAddRulePopup = true }
            )
        }
        if (showAddRulePopup) {
            EditRuleDialog(onDismiss = { showAddRulePopup = false })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LogsTab() {
        val coroutineScope = rememberCoroutineScope()

        val logs = remember { mutableStateListOf<TrackerLog>() }
        var lastTimestamp by remember { mutableLongStateOf(Long.MAX_VALUE) }
        var filterType by remember { mutableStateOf(FilterType.USERNAME) }

        var filter by remember { mutableStateOf("") }
        var searchTimeoutJob by remember { mutableStateOf<Job?>(null) }

        suspend fun loadNewLogs() {
            withContext(Dispatchers.IO) {
                logs.addAll(context.messageLogger.getLogs(lastTimestamp, filter = {
                    when (filterType) {
                        FilterType.USERNAME -> it.username.contains(filter, ignoreCase = true)
                        FilterType.CONVERSATION -> it.conversationTitle?.contains(filter, ignoreCase = true) == true || (it.username == filter && !it.isGroup)
                        FilterType.EVENT -> it.eventType.contains(filter, ignoreCase = true)
                    }
                }).apply {
                    lastTimestamp = minOfOrNull { it.timestamp } ?: lastTimestamp
                })
            }
        }

        suspend fun resetAndLoadLogs() {
            logs.clear()
            lastTimestamp = Long.MAX_VALUE
            loadNewLogs()
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showAutoComplete by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = showAutoComplete, onExpandedChange = { showAutoComplete = it }) {
                    TextField(
                        value = filter,
                        onValueChange = {
                            filter = it
                            coroutineScope.launch {
                                searchTimeoutJob?.cancel()
                                searchTimeoutJob = coroutineScope.launch {
                                    delay(200)
                                    showAutoComplete = true
                                    resetAndLoadLogs()
                                }
                            }
                        },
                        placeholder = { Text("Search") },
                        maxLines = 1,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1F)
                            .menuAnchor()
                            .padding(8.dp)
                    )

                    DropdownMenu(expanded = showAutoComplete, onDismissRequest = {
                        showAutoComplete = false
                    }, properties = PopupProperties(focusable = false)) {
                        val suggestedEntries = remember(filter) {
                            mutableStateListOf<String>()
                        }

                        LaunchedEffect(filter) {
                            suggestedEntries.addAll(when (filterType) {
                                FilterType.USERNAME -> context.messageLogger.findUsername(filter)
                                FilterType.CONVERSATION -> context.messageLogger.findConversation(filter) + context.messageLogger.findUsername(filter)
                                FilterType.EVENT -> TrackerEventType.entries.filter { it.name.contains(filter, ignoreCase = true) }.map { it.key }
                            }.take(5))
                        }

                        suggestedEntries.forEach { entry ->
                            DropdownMenuItem(onClick = {
                                filter = entry
                                coroutineScope.launch {
                                    resetAndLoadLogs()
                                }
                                showAutoComplete = false
                            }, text = {
                                Text(entry)
                            })
                        }
                    }
                }

                var dropDownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = dropDownExpanded, onExpandedChange = { dropDownExpanded = it }) {
                    ElevatedCard(
                        modifier = Modifier.menuAnchor()
                    ) {
                        Text("Filter " + filterType.name, modifier = Modifier.padding(8.dp))
                    }
                    DropdownMenu(expanded = dropDownExpanded, onDismissRequest = {
                        dropDownExpanded = false
                    }) {
                        FilterType.entries.forEach { type ->
                            DropdownMenuItem(onClick = {
                                filterType = type
                                dropDownExpanded = false
                                coroutineScope.launch {
                                    resetAndLoadLogs()
                                }
                            }, text = {
                                Text(type.name)
                            })
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                item {
                    if (logs.isEmpty()) {
                        Text("No logs found", modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Light)
                    }
                }
                items(logs) { log ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                Text(log.username + " " + log.eventType + " in " + log.conversationTitle)
                                Text(
                                    DateFormat.getDateTimeInstance().format(log.timestamp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }

                            OutlinedIconButton(
                                onClick = {
                                    context.messageLogger.deleteTrackerLog(log.id)
                                    logs.remove(log)
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    LaunchedEffect(lastTimestamp) {
                        loadNewLogs()
                    }
                }
            }
        }

    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun EditRuleDialog(
        ruleId: Int? = null,
        onDismiss: () -> Unit = {}
    ) {
        var currentRuleId by remember { mutableStateOf(ruleId) }
        val events = remember { mutableStateListOf<TrackerRuleEvent>() }

        LaunchedEffect(Unit) {
            currentRuleId = ruleId ?: context.modDatabase.addTrackerRule(null, null)
            events.addAll(context.modDatabase.getTrackerEvents(currentRuleId ?: return@LaunchedEffect).toMutableList())
        }

        fun saveRule() {
            events.forEach { event ->
                context.modDatabase.addOrUpdateTrackerRuleEvent(
                    event.id.takeIf { it > -1 },
                    currentRuleId,
                    event.eventType,
                    event.params,
                    event.actions
                )
            }
        }

        @Composable
        fun ActionCheckbox(
            text: String,
            checked: MutableState<Boolean>,
            onChanged: (Boolean) -> Unit = {}
        ) {
            Row(
                modifier = Modifier.clickable {
                    checked.value = !checked.value
                    onChanged(checked.value)
                },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    modifier = Modifier.size(30.dp),
                    checked = checked.value,
                    onCheckedChange = {
                        checked.value = it
                        onChanged(it)
                    }
                )
                Text(text, fontSize = 12.sp)
            }
        }

        AlertDialog(
            onDismissRequest = {
                onDismiss()
            },
            title = { Text("Rule $currentRuleId") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        var currentEventType by remember { mutableStateOf(TrackerEventType.CONVERSATION_ENTER.key) }
                        var checkedActions by remember { mutableStateOf(emptySet<TrackerRuleAction>()) }
                        val showDropdown = remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ExposedDropdownMenuBox(expanded = showDropdown.value, onExpandedChange = { showDropdown.value = it }) {
                                ElevatedButton(
                                    onClick = { showDropdown.value = true },
                                    modifier = Modifier.menuAnchor()
                                ) {
                                    Text(currentEventType)
                                }
                                DropdownMenu(expanded = showDropdown.value, onDismissRequest = { showDropdown.value = false }) {
                                    TrackerEventType.entries.forEach { eventType ->
                                        DropdownMenuItem(onClick = {
                                            currentEventType = eventType.key
                                            showDropdown.value = false
                                        }, text = {
                                            Text(eventType.key)
                                        })
                                    }
                                }
                            }

                            OutlinedButton(onClick = {
                                events.add(TrackerRuleEvent(-1, true, currentEventType, TrackerRuleActionParams(), checkedActions.toList()))
                            }) {
                                Text("Add")
                            }
                        }

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp),
                        ) {
                            TrackerRuleAction.entries.forEach { action ->
                                ActionCheckbox(action.name, checked = remember { mutableStateOf(checkedActions.contains(action)) }) {
                                    if (it) {
                                        checkedActions += action
                                    } else {
                                        checkedActions -= action
                                    }
                                }
                            }
                        }
                    }


                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(events) { event ->
                            var collapsed by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        collapsed = !collapsed
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(event.eventType)
                                    OutlinedIconButton(onClick = {
                                        if (event.id > -1) {
                                            context.modDatabase.deleteTrackerRuleEvent(event.id)
                                        }
                                        events.remove(event)
                                    }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                                if (collapsed) {
                                    Text(event.actions.joinToString(", ") { it.name }, fontSize = 10.sp, fontWeight = FontWeight.Light)
                                    ActionCheckbox(text = "Only inside conversation", checked = remember { mutableStateOf(event.params.onlyInsideConversation) }, onChanged = { event.params.onlyInsideConversation = it })
                                    ActionCheckbox(text = "Only outside conversation", checked = remember { mutableStateOf(event.params.onlyOutsideConversation) }, onChanged = { event.params.onlyOutsideConversation = it })
                                    ActionCheckbox(text = "Only when app active", checked = remember { mutableStateOf(event.params.onlyWhenAppActive) }, onChanged = { event.params.onlyWhenAppActive = it })
                                    ActionCheckbox(text = "Only when app inactive", checked = remember { mutableStateOf(event.params.onlyWhenAppInactive) }, onChanged = { event.params.onlyWhenAppInactive = it })
                                    ActionCheckbox(text = "No push notification when active", checked = remember { mutableStateOf(event.params.noPushNotificationWhenAppActive) }, onChanged = { event.params.noPushNotificationWhenAppActive = it })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    saveRule()
                    onDismiss()
                }) {
                    Text("Save")
                }
            },

            dismissButton = {
                Button(onClick = {
                    context.modDatabase.deleteTrackerRule(currentRuleId ?: return@Button)
                    onDismiss()
                }) {
                    Text("Delete")
                }
            }
        )
    }

    @Composable
    private fun ConfigRulesTab() {
        val rules = remember { mutableStateListOf<TrackerRule>() }
        var editRuleId by remember { mutableStateOf<Int?>(null) }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(rules) { rule ->
                    var eventCount by remember { mutableIntStateOf(0) }

                    LaunchedEffect(rule.id, editRuleId) {
                        launch(Dispatchers.IO) {
                            eventCount = context.modDatabase.getTrackerEvents(rule.id).size
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editRuleId = rule.id }
                            .padding(5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Rule ${rule.id} ${rule.conversationId?.let { "1 conversation" } ?: "All conversations" } - ${rule.userId?.let { "1 user" } ?: "All users"}")
                            Text("has $eventCount events")
                        }
                    }
                }
            }
        }

        if (editRuleId != null) {
            EditRuleDialog(editRuleId, onDismiss = {
                editRuleId = null
            })
        }

        LaunchedEffect(editRuleId != null) {
            rules.clear()
            launch(Dispatchers.IO) {
                rules.addAll(context.modDatabase.getTrackerRules(null, null))
            }
        }
    }


    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        currentPage = pagerState.currentPage

        Column {
            TabRow(selectedTabIndex = pagerState.currentPage, indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.pagerTabIndicatorOffset(
                        pagerState = pagerState,
                        tabPositions = tabPositions
                    )
                )
            }) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> LogsTab()
                    1 -> ConfigRulesTab()
                }
            }
        }
    }
}