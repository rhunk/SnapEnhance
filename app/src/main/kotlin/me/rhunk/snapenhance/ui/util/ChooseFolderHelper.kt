package me.rhunk.snapenhance.ui.util

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

object ChooseFolderHelper {
    fun createChooseFolder(activity: ComponentActivity, callback: (uri: String) -> Unit): () -> Unit {
        val activityResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) result@{
            if (it.resultCode != Activity.RESULT_OK) return@result
            val uri = it.data?.data ?: return@result
            val value = uri.toString()
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            callback(value)
        }

        return {
            activityResultLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
        }
    }
}