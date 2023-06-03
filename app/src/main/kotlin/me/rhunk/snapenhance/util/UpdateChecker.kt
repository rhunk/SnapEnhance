package me.rhunk.snapenhance.util

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray

class UpdateChecker {
	private fun updateCheck(context:Context) {
		val client = OkHttpClient()
		val endpoint = Request.Builder().url("https://api.github.com/repos/rhunk/SnapEnhance/releases").build()
		client.newCall(endpoint).enqueue(object :Callback {
			override fun onFailure(call:Call, e: IOException) {
				Logger.log(e)
			}
			
			override fun onResponse(call:Call, response:Response) {
				if (!response.isSuccessful) {
					Logger.log("API error: ${response.code} ${response.message}")
				}
				
				val releases = JSONArray(response.body.string())
				if (releases.length() > 0) {
					val latestRelease = releases.getJSONObject(0)
					val latestVersion = latestRelease.getString("tag_name")
					if (latestVersion.removePrefix("v") != BuildConfig.VERSION_NAME) {
						val releaseContentBody = latestRelease.getString("body")
						val downloadEndpoint = latestRelease.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
						val alert = AlertDialog.Builder(context)
						alert.setTitle("New Update available!")
							.setMessage("There is a new Update for SnapEnhance available! (${latestVersion})\n\n${releaseContentBody}")
							.setPositiveButton("Download and Install") { dialog, _ ->
								dialog.dismiss()
								Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
								val request = DownloadManager.Request(Uri.parse(downloadEndpoint))
									.setTitle("Downloading SnapEnhance APK...")
									.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "latest-snapenhance.apk")
									.setMimeType("application/vnd.android.package-archive")
									.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
								val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
								val onCompleteReceiver = object :BroadcastReceiver() {
									override fun onReceive(context: Context?, intent: Intent?) {
										val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
										if (id == downloadManager.enqueue(request)) {
											context?.unregisterReceiver(this)
											val install = Intent(Intent.ACTION_VIEW).apply {
												setDataAndType(downloadManager.getUriForDownloadedFile(downloadManager.enqueue(request)), "application/vnd.android.package-archive")
												flags = Intent.FLAG_ACTIVITY_NEW_TASK
											}
											context?.startActivity(install)
										}
									}
								}
								context.registerReceiver(onCompleteReceiver, IntentFilter(
									DownloadManager.ACTION_DOWNLOAD_COMPLETE)
								)
							}
							.setNegativeButton("Cancel") { dialog, _ ->
								dialog.dismiss()
							}
						alert.show()
					}
				}
			}
		})
	}
}