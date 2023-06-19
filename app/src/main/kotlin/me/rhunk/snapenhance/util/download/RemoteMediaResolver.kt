package me.rhunk.snapenhance.util.download

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64

object RemoteMediaResolver {
    private const val BOLT_HTTP_RESOLVER_URL = "https://aws.api.snapchat.com/bolt-http"
    const val CF_ST_CDN_D = "https://cf-st.sc-cdn.net/d/"

    private val urlCache = mutableMapOf<String, String>()

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val requestUrl = request.url.toString()

            if (urlCache.containsKey(requestUrl)) {
                val cachedUrl = urlCache[requestUrl]!!
                return@addInterceptor chain.proceed(request.newBuilder().url(cachedUrl).build())
            }

            chain.proceed(request).apply {
                val responseUrl = this.request.url.toString()
                if (responseUrl.startsWith("https://cf-st.sc-cdn.net")) {
                    urlCache[requestUrl] = responseUrl
                }
            }
        }
        .build()

    fun downloadBoltMedia(protoKey: ByteArray): InputStream? {
        val request = Request.Builder()
            .url(BOLT_HTTP_RESOLVER_URL + "/resolve?co=" + Base64.getUrlEncoder().encodeToString(protoKey))
            .addHeader("User-Agent", Constants.USER_AGENT)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.log("Unexpected code $response")
                return null
            }
            return ByteArrayInputStream(response.body.bytes())
        }
    }
}
