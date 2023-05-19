package me.rhunk.snapenhance.util.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object CdnDownloader {
    const val BOLT_CDN_U = "https://bolt-gcdn.sc-cdn.net/u/"
    const val BOLT_CDN_X = "https://bolt-gcdn.sc-cdn.net/x/"
    const val CF_ST_CDN_D = "https://cf-st.sc-cdn.net/d/"
    const val CF_ST_CDN_F = "https://cf-st.sc-cdn.net/f/"
    const val CF_ST_CDN_H = "https://cf-st.sc-cdn.net/h/"
    const val CF_ST_CDN_G = "https://cf-st.sc-cdn.net/g/"
    const val CF_ST_CDN_O = "https://cf-st.sc-cdn.net/o/"
    const val CF_ST_CDN_I = "https://cf-st.sc-cdn.net/i/"
    const val CF_ST_CDN_J = "https://cf-st.sc-cdn.net/j/"
    const val CF_ST_CDN_C = "https://cf-st.sc-cdn.net/c/"
    const val CF_ST_CDN_M = "https://cf-st.sc-cdn.net/m/"
    const val CF_ST_CDN_A = "https://cf-st.sc-cdn.net/a/"
    const val CF_ST_CDN_AA = "https://cf-st.sc-cdn.net/aa/"

    private val keyCache: MutableMap<String, String> = mutableMapOf()

    fun downloadRemoteContent(
        key: String,
        vararg endpoints: String
    ): InputStream? = runBlocking {
        if (keyCache.containsKey(key)) {
            return@runBlocking queryRemoteContent(
                keyCache[key]!!
            )
        }
        val jobs = mutableListOf<Job>()
        var inputStream: InputStream? = null

        endpoints.forEach {
            launch(Dispatchers.IO) {
                val url = it + key
                queryRemoteContent(url)?.let { result ->
                    keyCache[key] = url
                    inputStream = result
                    jobs.forEach { it.cancel() }
                }
            }.also { jobs.add(it) }
        }
        jobs.joinAll()
        inputStream
    }


    private fun queryRemoteContent(url: String): InputStream? {
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.setRequestProperty("User-Agent", Constants.USER_AGENT)
            return connection.inputStream
        } catch (ignored: Throwable) {
        }
        return null
    }

    //TODO: automatically detect the correct endpoint
    fun downloadWithDefaultEndpoints(key: String): InputStream? {
        return downloadRemoteContent(
            key,
            CF_ST_CDN_F,
            CF_ST_CDN_H,
            BOLT_CDN_U,
            BOLT_CDN_X,
            CF_ST_CDN_O,
            CF_ST_CDN_I,
            CF_ST_CDN_C,
            CF_ST_CDN_J,
            CF_ST_CDN_M,
            CF_ST_CDN_A,
            CF_ST_CDN_AA,
            CF_ST_CDN_G,
            CF_ST_CDN_D
        )
    }
}
