package me.rhunk.snapenhance.common.util.snap

import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.logger.AbstractLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.Base64

object RemoteMediaResolver {
    private const val BOLT_HTTP_RESOLVER_URL = "https://web.snapchat.com/bolt-http"
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

    private fun newResolveRequest(protoKey: ByteArray) = Request.Builder()
        .url(BOLT_HTTP_RESOLVER_URL + "/resolve?co=" + Base64.getUrlEncoder().encodeToString(protoKey))
        .addHeader("User-Agent", Constants.USER_AGENT)
        .build()

    /**
     * Download bolt media with memory allocation
     */
    fun downloadBoltMedia(protoKey: ByteArray, decryptionCallback: (InputStream) -> InputStream = { it }): ByteArray? {
        okHttpClient.newCall(newResolveRequest(protoKey)).execute().use { response ->
            if (!response.isSuccessful) {
                AbstractLogger.directDebug("Unexpected code $response")
                return null
            }
            return decryptionCallback(response.body.byteStream()).readBytes()
        }
    }
    
    fun downloadBoltMedia(protoKey: ByteArray, decryptionCallback: (InputStream) -> InputStream = { it }, resultCallback: (stream: InputStream, length: Long) -> Unit) {
        okHttpClient.newCall(newResolveRequest(protoKey)).execute().use { response ->
            if (!response.isSuccessful) {
                throw Throwable("invalid response ${response.code}")
            }
            resultCallback(
                decryptionCallback(
                    response.body.byteStream()
                ),
                response.body.contentLength()
            )
        }
    }
}
