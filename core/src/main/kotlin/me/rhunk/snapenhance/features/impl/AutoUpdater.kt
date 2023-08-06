package me.rhunk.snapenhance.features.impl

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import okhttp3.OkHttpClient
import okhttp3.Request

class AutoUpdater : Feature("AutoUpdater", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val autoUpdaterTime = context.config.global.autoUpdater.getNullable() ?: return
        val currentTimeMillis = System.currentTimeMillis()
        val checkForUpdatesTimestamp = context.bridgeClient.getAutoUpdaterTime()

        val delayTimestamp = when (autoUpdaterTime) {
            "EVERY_LAUNCH" -> currentTimeMillis - checkForUpdatesTimestamp
            "DAILY" -> 86400000L
            "WEEKLY" -> 604800000L
            else -> return
        }

        if (checkForUpdatesTimestamp + delayTimestamp > currentTimeMillis) return

        runCatching {
            checkForUpdates()
        }.onFailure {
            Logger.error("Failed to check for updates: ${it.message}", it)
        }.onSuccess {
            context.bridgeClient.setAutoUpdaterTime(currentTimeMillis)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun checkForUpdates(): String? {
        val endpoint = Request.Builder().url("https://api.github.com/repos/rhunk/SnapEnhance/releases").build()
        val response = OkHttpClient().newCall(endpoint).execute()

        if (!response.isSuccessful) throw Throwable("Failed to fetch releases: ${response.code}")

        val releases = JsonParser.parseString(response.body.string()).asJsonArray.also {
            if (it.size() == 0) throw Throwable("No releases found")
        }

        val latestRelease = releases.get(0).asJsonObject
        val latestVersion = latestRelease.getAsJsonPrimitive("tag_name").asString
        if (latestVersion.removePrefix("v") == BuildConfig.VERSION_NAME) return null

        val architectureName = Build.SUPPORTED_ABIS.let {
            if (it.contains("arm64-v8a")) return@let "armv8"
            if (it.contains("armeabi-v7a") || it.contains("armeabi")) return@let "armv7"
            throw Throwable("Failed getting architecture")
        }

        val releaseContentBody = latestRelease.getAsJsonPrimitive("body").asString
        val downloadEndpoint = "https://github.com/rhunk/SnapEnhance/releases/download/${latestVersion}/app-${latestVersion.removePrefix("v")}-${architectureName}-release-signed.apk"

        context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(context.translation["auto_updater.dialog_title"])
                .setMessage(
                    context.translation.format("auto_updater.dialog_message",
                        "version" to latestVersion,
                        "body" to releaseContentBody)
                )
                .setNegativeButton(context.translation["auto_updater.dialog_negative_button"]) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(context.translation["auto_updater.dialog_positive_button"]) { dialog, _ ->
                    dialog.dismiss()
                    context.longToast(context.translation["auto_updater.downloading_toast"])

                    val request = DownloadManager.Request(Uri.parse(downloadEndpoint))
                        .setTitle(context.translation["auto_updater.download_manager_notification_title"])
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "latest-snapenhance.apk")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

                    val downloadManager = context.androidContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId = downloadManager.enqueue(request)

                    val onCompleteReceiver = object: BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                            if (id != downloadId) return
                            context.unregisterReceiver(this)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(downloadManager.getUriForDownloadedFile(downloadId), "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }
                    }

                    context.mainActivity?.registerReceiver(onCompleteReceiver, IntentFilter(
                        DownloadManager.ACTION_DOWNLOAD_COMPLETE
                    ))
                }.show()
        }

        return latestVersion
    }
}