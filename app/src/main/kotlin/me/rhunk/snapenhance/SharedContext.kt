package me.rhunk.snapenhance

import android.content.Context
import me.rhunk.snapenhance.bridge.TranslationWrapper
import me.rhunk.snapenhance.download.DownloadTaskManager
import me.rhunk.snapenhance.util.download.DownloadServer

/**
 * Used to store objects between activities and receivers
 */
object SharedContext {
    lateinit var downloadTaskManager: DownloadTaskManager
    lateinit var translation: TranslationWrapper
    lateinit var downloadServer: DownloadServer

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
        if (!this::downloadServer.isInitialized) {
            downloadServer = DownloadServer()
        }
    }
}