package me.rhunk.snapenhance.manager.data

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import kotlin.math.absoluteValue

@Parcelize
data class DownloadItem(
    val title: String,
    val releaseDate: String,
    val downloadPage: String
): Parcelable {
    @IgnoredOnParcel
    val shortTitle = title.substringBefore("(").trim()
    @IgnoredOnParcel
    val hash = (title + releaseDate + downloadPage).hashCode().absoluteValue.toString(16)
    @IgnoredOnParcel
    val isBeta = title.contains("Beta", ignoreCase = true)
}

class APKMirror {
    val okhttpClient = OkHttpClient.Builder().addInterceptor {
        it.proceed(
            it.request().newBuilder()
                .addHeader("User-Agent", System.getProperty("http.agent")!!)
                .build()
        )
    }.build()

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com"
        private const val FETCH_BUILD_URL = "$BASE_URL/apk/snap-inc/snapchat/variant-%7B%22arches_slug%22%3A%5B%22arm64-v8a%22%2C%22armeabi-v7a%22%5D%2C%22dpis_slug%22%3A%5B%22nodpi%22%5D%7D/page/{page}/"
    }

    fun fetchDownloadLink(downloadPageUri: String): String? {
        okhttpClient.newCall(
            Request.Builder()
                .url("$BASE_URL$downloadPageUri")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            val finalDownloadPageUri = Jsoup.parse(response.body.string()).getElementsByClass("downloadButton").first()?.attr("href")

            okhttpClient.newCall(
                Request.Builder()
                    .url("$BASE_URL$finalDownloadPageUri")
                    .build()
            ).execute().use { response2 ->
                if (!response2.isSuccessful) return null
                val document = Jsoup.parse(response2.body.string())
                val downloadForm = document.getElementById("filedownload") ?: return null
                val arguments = downloadForm.childNodes().mapNotNull {
                    (it.attr("name") to it.attr("value")).takeIf { pair -> pair.second.isNotEmpty() }
                }
                return BASE_URL + downloadForm.attr("action") + "?" + arguments.joinToString("&") { "${it.first}=${it.second}" }
            }
        }
    }

    fun fetchSnapchatVersions(page: Int = 1): List<DownloadItem>? {
        val versions = mutableListOf<DownloadItem>()
        okhttpClient.newCall(
            Request.Builder()
                .url(FETCH_BUILD_URL.replace("{page}", page.toString()))
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            val document = Jsoup.parse(response.body.string())
            document.getElementById("primary")?.getElementsByClass("appRow")?.forEach { app ->
                val title = app.getElementsByTag("h5").first()?.attr("title") ?: return@forEach
                val releaseDate = app.getElementsByClass("dateyear_utc").attr("data-utcdate") ?: return@forEach
                val downloadPage = app.getElementsByClass("downloadLink").first()?.attr("href") ?: return@forEach

                versions.add(DownloadItem(title, releaseDate, downloadPage))
            }
        }
        return versions
    }
}