package me.rhunk.snapenhance.manager.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import me.rhunk.snapenhance.core.config.PropertyValue
import kotlin.system.exitProcess

object SaveFolderChecker {
    fun askForFolder(activity: ComponentActivity, property: PropertyValue<String>, saveConfig: () -> Unit) {
        if (property.get().isEmpty() || !property.get().startsWith("content://")) {
            val startActivity = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) result@{
                if (it.resultCode != Activity.RESULT_OK) return@result
                val uri = it.data?.data ?: return@result
                val value = uri.toString()
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                property.set(value)
                saveConfig()
                Toast.makeText(activity, "save folder set!", Toast.LENGTH_SHORT).show()
                activity.finish()
            }

            AlertDialog.Builder(activity)
                .setTitle("Save folder")
                .setMessage("Please select a folder where you want to save downloaded files.")
                .setPositiveButton("Select") { _, _ ->
                    startActivity.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    )
                }
                .setNegativeButton("Cancel") { _, _ ->
                    exitProcess(0)
                }
                .show()
        }
    }
}