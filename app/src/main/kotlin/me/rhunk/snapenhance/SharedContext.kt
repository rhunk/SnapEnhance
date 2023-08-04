package me.rhunk.snapenhance

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import me.rhunk.snapenhance.bridge.wrapper.ConfigWrapper
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper
import me.rhunk.snapenhance.download.DownloadTaskManager
import kotlin.system.exitProcess

/**
 * Used to store objects between activities and receivers
 */
object SharedContext {
    lateinit var downloadTaskManager: DownloadTaskManager
    lateinit var translation: TranslationWrapper
    lateinit var config: ConfigWrapper

    private fun askForStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            exitProcess(0)
        }
        if (context !is Activity) {
            Logger.log("Storage permission not granted, exiting")
            exitProcess(0)
        }
        context.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 0)
    }

    private fun askForPermissions(context: Context) {

        //ask for storage permission
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasStoragePermission) return

        if (context !is Activity) {
            askForStoragePermission(context)
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Storage permission")
            .setMessage("App needs storage permission to download files and save them to your device. Please allow it in the next screen.")
            .setPositiveButton("Grant") { _, _ ->
                askForStoragePermission(context)
            }
            .setNegativeButton("Cancel") { _, _ ->
                exitProcess(0)
            }
            .show()
    }

    fun ensureInitialized(context: Context) {
        if (!this::downloadTaskManager.isInitialized) {
            downloadTaskManager = DownloadTaskManager().apply {
                init(context)
            }
        }
        if (!this::translation.isInitialized) {
            translation = TranslationWrapper().apply {
                loadFromContext(context)
            }
        }
        if (!this::config.isInitialized) {
            config = ConfigWrapper().apply { loadFromContext(context) }
        }

        askForPermissions(context)
    }
}