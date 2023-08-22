package me.rhunk.snapenhance.ui.util

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import me.rhunk.snapenhance.Logger

class ActivityLauncherHelper(
    val activity: ComponentActivity
) {
    private var callback: ((Intent) -> Unit)? = null
    private var activityResultLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == ComponentActivity.RESULT_OK) {
                runCatching {
                    callback?.let { it(result.data!!) }
                }.onFailure {
                    Logger.error("Failed to process activity result", it)
                }
            }
            callback = null
        }

    fun launch(intent: Intent, callback: (Intent) -> Unit) {
        if (this.callback != null) {
            throw IllegalStateException("Already launching an activity")
        }
        this.callback = callback
        activityResultLauncher.launch(intent)
    }
}

fun ActivityLauncherHelper.chooseFolder(callback: (uri: String) -> Unit) {
    launch(
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    ) {
        val uri = it.data ?: return@launch
        val value = uri.toString()
        this.activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        callback(value)
    }
}

fun ActivityLauncherHelper.saveFile(name: String, type: String = "*/*", callback: (uri: String) -> Unit) {
    launch(
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    ) {
        val uri = it.data ?: return@launch
        val value = uri.toString()
        this.activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        callback(value)
    }
}
fun ActivityLauncherHelper.openFile(type: String = "*/*", callback: (uri: String) -> Unit) {
    launch(
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    ) {
        val uri = it.data ?: return@launch
        val value = uri.toString()
        this.activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        callback(value)
    }
}