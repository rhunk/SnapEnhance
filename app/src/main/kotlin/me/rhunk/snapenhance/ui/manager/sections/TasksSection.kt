package me.rhunk.snapenhance.ui.manager.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.task.PendingTask
import me.rhunk.snapenhance.task.PendingTaskListener
import me.rhunk.snapenhance.task.Task
import me.rhunk.snapenhance.task.TaskStatus
import me.rhunk.snapenhance.task.TaskType
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.OnLifecycleEvent

class TasksSection : Section() {
    private var activeTasks by mutableStateOf(listOf<PendingTask>())
    private lateinit var recentTasks: MutableList<Task>

    @Composable
    override fun TopBarActions(rowScope: RowScope) {
        var showConfirmDialog by remember { mutableStateOf(false) }

        IconButton(onClick = {
            showConfirmDialog = true
        }) {
            Icon(Icons.Filled.Delete, contentDescription = "Clear all tasks")
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Clear all tasks") },
                text = { Text("Are you sure you want to clear all tasks?") },
                confirmButton = {
                    Button(
                        onClick = {
                            context.taskManager.clearAllTasks()
                            recentTasks.clear()
                            activeTasks.forEach {
                                runCatching {
                                    it.cancel()
                                }.onFailure { throwable ->
                                    context.log.error("Failed to cancel task $it", throwable)
                                }
                            }
                            activeTasks = listOf()
                            context.taskManager.getActiveTasks().clear()
                            showConfirmDialog = false
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                        }
                    ) {
                        Text("No")
                    }
                }
            )
        }
    }

    @Composable
    private fun TaskCard(modifier: Modifier, task: Task, pendingTask: PendingTask? = null) {
        var taskStatus by remember { mutableStateOf(task.status) }
        var taskProgressLabel by remember { mutableStateOf<String?>(null) }
        var taskProgress by remember { mutableIntStateOf(-1) }

        val listener = remember { PendingTaskListener(
            onStateChange = {
                taskStatus = it
            },
            onProgress = { label, progress ->
                taskProgressLabel = label
                taskProgress = progress
            }
        ) }

        LaunchedEffect(Unit) {
            pendingTask?.addListener(listener)
        }

        DisposableEffect(Unit) {
            onDispose {
                pendingTask?.removeListener(listener)
            }
        }

        OutlinedCard(modifier = modifier) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.padding(end = 15.dp)
                ) {
                    when (task.type) {
                        TaskType.DOWNLOAD -> Icon(Icons.Filled.Download, contentDescription = "Download")
                        TaskType.CHAT_ACTION -> Icon(Icons.Filled.ChatBubble, contentDescription = "Chat Action")
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    Text(task.hash, style = MaterialTheme.typography.labelSmall)
                    Column(
                        modifier = Modifier.padding(top = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (taskStatus.isFinalStage()) {
                            if (taskStatus != TaskStatus.SUCCESS) {
                                Text("$taskStatus", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            taskProgressLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (taskProgress != -1) {
                                LinearProgressIndicator(
                                    progress = taskProgress.toFloat() / 100f,
                                    strokeCap = StrokeCap.Round
                                )
                            } else {
                                task.extra?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Column {
                    if (pendingTask != null && !taskStatus.isFinalStage()) {
                        FilledIconButton(onClick = {
                            runCatching {
                                pendingTask.cancel()
                            }.onFailure { throwable ->
                                context.log.error("Failed to cancel task $pendingTask", throwable)
                            }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        when (taskStatus) {
                            TaskStatus.SUCCESS -> Icon(Icons.Filled.Check, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                            TaskStatus.FAILURE -> Icon(Icons.Filled.Error, contentDescription = "Failure", tint = MaterialTheme.colorScheme.error)
                            TaskStatus.CANCELLED -> Icon(Icons.Filled.Cancel, contentDescription = "Cancelled", tint = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    @Preview
    @Composable
    override fun Content() {
        val scrollState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        recentTasks = remember { mutableStateListOf() }
        var lastFetchedTaskId: Long? by remember { mutableStateOf(null) }

        fun fetchNewRecentTasks() {
            scope.launch(Dispatchers.IO) {
                val tasks = context.taskManager.fetchStoredTasks(lastFetchedTaskId ?: Long.MAX_VALUE)
                if (tasks.isNotEmpty()) {
                    lastFetchedTaskId = tasks.keys.last()
                    scope.launch {
                        val activeTaskIds = activeTasks.map { it.taskId }
                        recentTasks.addAll(tasks.filter { it.key !in activeTaskIds }.values)
                    }
                }
            }
        }

        fun fetchActiveTasks() {
            activeTasks = context.taskManager.getActiveTasks().values.sortedByDescending { it.taskId }.toMutableList()
        }

        LaunchedEffect(Unit) {
            fetchActiveTasks()
        }

        OnLifecycleEvent { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    fetchActiveTasks()
                }
            }
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                if (activeTasks.isEmpty() && recentTasks.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "No tasks", tint = MaterialTheme.colorScheme.primary)
                        Text("No tasks", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            items(activeTasks, key = { it.task.hash }) {pendingTask ->
                TaskCard(modifier = Modifier.padding(8.dp), pendingTask.task, pendingTask = pendingTask)
            }
            items(recentTasks, key = { it.hash }) { task ->
                TaskCard(modifier = Modifier.padding(8.dp), task)
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SideEffect {
                    fetchNewRecentTasks()
                }
            }
        }
    }
}