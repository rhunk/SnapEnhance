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
    private lateinit var activeTasks: MutableMap<Long, PendingTask>
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
                            activeTasks.toList().forEach {
                                runCatching {
                                    it.second.cancel()
                                }.onFailure { throwable ->
                                    context.log.error("Failed to cancel task ${it.first}", throwable)
                                }
                                activeTasks.remove(it.first)
                            }
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
        ).also { pendingTask?.addListener(it) }}

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
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
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
        activeTasks = remember { mutableStateMapOf() }
        recentTasks = remember { mutableStateListOf() }
        var lastFetchedTaskId: Long? by remember { mutableStateOf(null) }

        fun fetchNewRecentTasks() {
            scope.launch(Dispatchers.IO) {
                val tasks = context.taskManager.fetchStoredTasks(lastFetchedTaskId ?: Long.MAX_VALUE)
                if (tasks.isNotEmpty()) {
                    lastFetchedTaskId = tasks.keys.last()
                    recentTasks.addAll(tasks.filter { !activeTasks.containsKey(it.key) }.values)
                }
            }
        }

        fun fetchActiveTasks() {
            scope.launch {
                activeTasks.clear()
                activeTasks.putAll(context.taskManager.getActiveTasks())
            }
        }

        SideEffect {
            fetchActiveTasks()
        }

        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    fetchActiveTasks()
                }
                else -> {}
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
            items(activeTasks.size) { index ->
                val pendingTask = activeTasks.values.elementAt(index)
                TaskCard(modifier = Modifier.padding(8.dp), pendingTask.task, pendingTask = pendingTask)
            }
            items(recentTasks) { task ->
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