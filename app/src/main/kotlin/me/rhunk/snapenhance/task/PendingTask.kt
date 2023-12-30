package me.rhunk.snapenhance.task


enum class TaskType(
    val key: String
) {
    DOWNLOAD("download"),
    CHAT_ACTION("chat_action");

    companion object {
        fun fromKey(key: String): TaskType {
            return entries.find { it.key == key } ?: throw IllegalArgumentException("Invalid key $key")
        }
    }
}

enum class TaskStatus(
    val key: String
) {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILURE("failure"),
    CANCELLED("cancelled");

    fun isFinalStage(): Boolean {
        return this == SUCCESS || this == FAILURE || this == CANCELLED
    }

    companion object {
        fun fromKey(key: String): TaskStatus {
            return entries.find { it.key == key } ?: throw IllegalArgumentException("Invalid key $key")
        }
    }
}

data class PendingTaskListener(
    val onSuccess: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onProgress: (label: String?, progress: Int) -> Unit = { _, _ -> },
    val onStateChange: (status: TaskStatus) -> Unit = {},
)

data class Task(
    val type: TaskType,
    val title: String,
    val author: String?,
    val hash: String
) {
    var changeListener: () -> Unit = {}

    var extra: String? = null
        set(value) {
            field = value
            changeListener()
        }
    var status: TaskStatus = TaskStatus.PENDING
        set(value) {
            field = value
            changeListener()
        }
}

class PendingTask(
    val taskId: Long,
    val task: Task
) {
    private val listeners = mutableListOf<PendingTaskListener>()

    fun addListener(listener: PendingTaskListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: PendingTaskListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    var status
        get() = task.status;
        set(value) {
            task.status = value;
            synchronized(listeners) {
                listeners.forEach { it.onStateChange(value) }
            }
        }

    var progressLabel: String? = null
        set(value) {
            field = value
            synchronized(listeners) {
                listeners.forEach { it.onProgress(value, progress) }
            }
        }

    private var _progress = 0
        set(value) {
            assert(value in 0..100 || value == -1)
            field = value
        }

    var progress get() = _progress
        set(value) {
            _progress = value
            synchronized(listeners) {
                listeners.forEach { it.onProgress(progressLabel, value) }
            }
        }

    fun updateProgress(label: String, progress: Int = -1) {
        _progress = progress.coerceIn(-1, 100)
        progressLabel = label
    }

    fun fail(reason: String) {
        status = TaskStatus.FAILURE
        synchronized(listeners) {
            listeners.forEach { it.onCancel() }
        }
        updateProgress(reason)
    }

    fun success() {
        status = TaskStatus.SUCCESS
        synchronized(listeners) {
            listeners.forEach { it.onSuccess() }
        }
    }

    fun cancel() {
        status = TaskStatus.CANCELLED
        synchronized(listeners) {
            listeners.forEach { it.onCancel() }
        }
    }
}