package me.rhunk.snapenhance.ui.manager.data

import com.google.gson.JsonParser
import me.rhunk.snapenhance.common.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request


object Updater {
    data class LatestRelease(
        val versionName: String,
        val releaseUrl: String
    )

    fun checkForLatestRelease(): LatestRelease? {
        val endpoint = Request.Builder().url("https://api.github.com/repos/rhunk/SnapEnhance/releases").build()
        val response = OkHttpClient().newCall(endpoint).execute()

        if (!response.isSuccessful) throw Throwable("Failed to fetch releases: ${response.code}")

        val releases = JsonParser.parseString(response.body.string()).asJsonArray.also {
            if (it.size() == 0) throw Throwable("No releases found")
        }

        val latestRelease = releases.get(0).asJsonObject
        val latestVersion = latestRelease.getAsJsonPrimitive("tag_name").asString
        if (latestVersion.removePrefix("v") == BuildConfig.VERSION_NAME) return null

        return LatestRelease(latestVersion, endpoint.url.toString().replace("api.", "").replace("repos/", ""))
    }
}