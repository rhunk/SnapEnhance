package me.rhunk.snapenhance.common.scripting.impl

import me.rhunk.snapenhance.common.scripting.bindings.AbstractBinding
import me.rhunk.snapenhance.common.scripting.bindings.BindingSide
import me.rhunk.snapenhance.common.scripting.ktx.contextScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter


class Networking : AbstractBinding("networking", BindingSide.COMMON) {
    private val defaultHttpClient = OkHttpClient()

    inner class RequestBuilderWrapper(
        val requestBuilder: Request.Builder
    ) {
        @JSFunction
        fun url(url: String) = requestBuilder.url(url).let { this }

        @JSFunction
        fun addHeader(name: String, value: String) = requestBuilder.addHeader(name, value).let { this }

        @JSFunction
        fun removeHeader(name: String) = requestBuilder.removeHeader(name).let { this }

        @JSFunction
        fun method(method: String, body: String) = requestBuilder.method(method, body.toRequestBody(null)).let { this }

        @JSFunction
        fun method(method: String, body: java.io.InputStream) = requestBuilder.method(method, body.readBytes().toRequestBody(null)).let { this }

        @JSFunction
        fun method(method: String, body: ByteArray) = requestBuilder.method(method, body.toRequestBody(null)).let { this }
    }

    inner class ResponseWrapper(
        private val response: Response
    ) {
        @get:JSGetter
        val statusCode get() = response.code
        @get:JSGetter
        val statusMessage get() = response.message
        @get:JSGetter
        val headers get() = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }
        @get:JSGetter
        val bodyAsString get() = response.body.string()
        @get:JSGetter
        val bodyAsStream get() = response.body.byteStream()
        @get:JSGetter
        val bodyAsByteArray get() = response.body.bytes()
        @get:JSGetter
        val contentLength get() = response.body.contentLength()
        @JSFunction fun getHeader(name: String) = response.header(name)
        @JSFunction fun close() = response.close()
    }

    inner class WebsocketWrapper(
        private val websocket: WebSocket
    ) {
        @JSFunction fun cancel() = websocket.cancel()
        @JSFunction fun close(code: Int, reason: String) = websocket.close(code, reason)
        @JSFunction fun queueSize() = websocket.queueSize()
        @JSFunction fun send(bytes: ByteArray) = websocket.send(bytes.toByteString())
        @JSFunction fun send(text: String) = websocket.send(text)
    }

    @JSFunction
    fun getUrl(url: String, callback: (error: String?, response: String) -> Unit) {
        defaultHttpClient.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(e.message, "")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    callback(null, it.body.string())
                }
            }
        })
    }

    @JSFunction
    fun getUrlAsStream(url: String, callback: (error: String?, response: java.io.InputStream) -> Unit) {
        defaultHttpClient.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(e.message, java.io.ByteArrayInputStream(byteArrayOf()))
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    callback(null, it.body.byteStream())
                }
            }
        })
    }

    @JSFunction
    fun newRequest() = RequestBuilderWrapper(Request.Builder())

    @JSFunction
    fun newWebSocket(requestBuilder: RequestBuilderWrapper, listener: Scriptable): WebsocketWrapper {
        return defaultHttpClient.newWebSocket(requestBuilder.requestBuilder.build(), object: WebSocketListener() {
            private fun callListener(name: String, websocket: WebSocket, vararg args: Any?) {
                contextScope {
                    (listener.get(name, listener) as? Function)?.call(this, listener, listener, arrayOf(WebsocketWrapper(websocket), *args))
                }
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                callListener("onOpen", webSocket, ResponseWrapper(response))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callListener("onClosed", webSocket, code, reason)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                callListener("onClosing", webSocket, code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callListener("onFailure", webSocket, t.message, response?.let { ResponseWrapper(it) })
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                callListener("onMessageBytes", webSocket, bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                callListener("onMessageText", webSocket, text)
            }
        }).let { WebsocketWrapper(it) }
    }

    @JSFunction
    fun enqueue(requestBuilder: RequestBuilderWrapper, callback: (error: String?, response: ResponseWrapper?) -> Unit) {
        defaultHttpClient.newCall(requestBuilder.requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(e.message, null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    callback(null, ResponseWrapper(it))
                }
            }
        })
    }

    @JSFunction
    fun execute(requestBuilder: RequestBuilderWrapper) = ResponseWrapper(defaultHttpClient.newCall(requestBuilder.requestBuilder.build()).execute())

    override fun getObject() = this
}