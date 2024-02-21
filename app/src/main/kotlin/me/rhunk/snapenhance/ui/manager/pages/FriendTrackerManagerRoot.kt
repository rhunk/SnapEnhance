package me.rhunk.snapenhance.ui.manager.pages

import androidx.compose.foundation.ExperimentalFoundationApi
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
import me.rhunk.snapenhance.common.data.TrackerRuleEvent
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset
import java.text.DateFormat


@OptIn(ExperimentalFoundationApi::class)
class FriendTrackerManagerRoot : Routes.Route() {
    enum class FilterType {
        CONVERSATION, USERNAME, EVENT
    }

    private val titles = listOf("Logs", "Config Rules")
    private var currentPage by mutableIntStateOf(0)

    override val floatingActionButton: @Composable () -> Unit = {
        if (currentPage == 1) {
            ExtendedFloatingActionButton(
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Rule") },
                expanded = false,
                text = {},
                onClick = {}
            )
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
                        Text("No logs found", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Light)
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

    @Composable
    @OptIn(ExperimentalLayoutApi::class)
    private fun ConfigRulesTab() {
        val rules = remember { mutableStateListOf<TrackerRule>() }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(rules) { rule ->
                    val events = remember(rule.id) {
                        mutableStateListOf<TrackerRuleEvent>()
                    }

                    LaunchedEffect(rule.id) {
                        withContext(Dispatchers.IO) {
                            events.addAll(context.modDatabase.getTrackerEvents(rule.id))
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Rule: ${rule.id} - conversationId: ${rule.conversationId?.let { "present" } ?: "none" } - userId: ${rule.userId?.let { "present" } ?: "none"}")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                events.forEach { event ->
                                    Text("${event.eventType} - ${event.flags}")
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            rules.addAll(context.modDatabase.getTrackerRules(null, null))
        }
    }


    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        currentPage = pagerState.currentPage

        Column {
            TabRow(selectedTabIndex = pagerState.currentPage, indicator = { tabPositions ->
                TabRowDefaults.Indicator(
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