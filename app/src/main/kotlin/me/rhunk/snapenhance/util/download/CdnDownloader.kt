package me.rhunk.snapenhance.util.download

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.util.protobuf.ProtoWriter
import java.io.InputStream
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

object CdnDownloader {
    private const val BOLT_HTTP_RESOLVER_URL = "https://aws.api.snapchat.com/bolt-http"
    const val CF_ST_CDN_D = "https://cf-st.sc-cdn.net/d/"

    private fun queryRemoteContent(url: String): InputStream? {
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", Constants.USER_AGENT)
            return connection.inputStream
        } catch (ignored: Throwable) {
        }
        return null
    }

    fun downloadWithDefaultEndpoints(key: String): InputStream? {
        val payload = ProtoWriter().apply {
            write(2) {
                writeString(2, key)
                writeBuffer(3, byteArrayOf())
                writeBuffer(3, byteArrayOf())
                writeConstant(6, 6)
                writeConstant(10, 4)
                writeConstant(12, 1)
            }
        }.toByteArray()
        return queryRemoteContent(BOLT_HTTP_RESOLVER_URL + "/resolve?co=" + Base64.getUrlEncoder().encodeToString(payload))
    }
}
