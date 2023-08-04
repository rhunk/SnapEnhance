package me.rhunk.snapenhance

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.download.DownloadTaskManager
import kotlin.system.exitProcess

/**
 * Used to store objects between activities and receivers
 */
object SharedContext {
    lateinit var downloadTaskManager: DownloadTaskManager
    lateinit var translation: LocaleWrapper

    fun ensureInitialized(context: Context) {
        if (!this::downloadTaskManager.isInitialized) {
            downloadTaskManager = DownloadTaskManager().apply {
                init(context)
            }
        }
        if (!this::translation.isInitialized) {
            translation = LocaleWrapper().apply {
                loadFromContext(context)
            }
        }
        //askForPermissions(context)
    }
}