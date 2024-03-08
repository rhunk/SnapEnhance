package me.rhunk.snapenhance.core.util.media

import kotlinx.coroutines.*
import me.rhunk.snapenhance.common.logger.AbstractLogger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.StringTokenizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class HttpServer(
    private val timeout: Int = 10000
) {
    private fun newRandomPort() = Random.nextInt(10000, 65535)

    var port = newRandomPort()
        private set

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var timeoutJob: Job? = null
    private var socketJob: Job? = null

    abstract class HttpBody {
        abstract val readBytes: (byteArray: ByteArray) -> Int
        open val onOpen: () -> Unit = {}
        open val onClose: () -> Unit = {}
    }

    abstract class HttpContent {
        abstract val contentType: String
        abstract val chunked: Boolean
        abstract val contentLength: Long?
        abstract val newBody: () -> HttpBody
    }

    private val cachedData = ConcurrentHashMap<String, HttpContent>()
    private var serverSocket: ServerSocket? = null

    fun ensureServerStarted(): HttpServer? {
        if (serverSocket != null && serverSocket?.isClosed != true) return this

        return runBlocking {
            withTimeoutOrNull(5000L) {
                suspendCoroutine { continuation ->
                    coroutineScope.launch(Dispatchers.IO) {
                        AbstractLogger.directDebug("Starting http server on port $port")
                        for (i in 0..5) {
                            try {
                                serverSocket = ServerSocket(port)
                                break
                            } catch (e: Throwable) {
                                AbstractLogger.directError("failed to start http server on port $port", e)
                                port = newRandomPort()
                            }
                        }
                        continuation.resumeWith(Result.success(if (serverSocket == null) null.also {
                            return@launch
                        } else this@HttpServer))

                        while (!serverSocket!!.isClosed) {
                            try {
                                val socket = serverSocket!!.accept()
                                timeoutJob?.cancel()
                                launch {
                                    handleRequest(socket)
                                    timeoutJob = launch {
                                        delay(timeout.toLong())
                                        AbstractLogger.directDebug("http server closed due to timeout")
                                        runCatching {
                                            socketJob?.cancel()
                                            socket.close()
                                            serverSocket?.close()
                                        }.onFailure {
                                            AbstractLogger.directError("failed to close socket", it)
                                        }
                                    }
                                }
                            } catch (e: SocketException) {
                                AbstractLogger.directDebug("http server timed out")
                                break;
                            } catch (e: Throwable) {
                                AbstractLogger.directError("failed to handle request", e)
                            }
                        }
                    }.also { socketJob = it }
                }
            }
        }
    }

    fun close() {
        runCatching {
            serverSocket?.close()
        }
    }

    fun putDownloadableContent(inputStream: InputStream, size: Long): String {
        val key = System.nanoTime().toString(16)
        cachedData[key] = object : HttpContent() {
            override val contentType: String = "application/octet-stream"
            override val chunked: Boolean = false
            override val contentLength: Long = size
            override val newBody: () -> HttpBody = {
                object : HttpBody() {
                    override val readBytes: (byteArray: ByteArray) -> Int = { byteArray ->
                        inputStream.read(byteArray)
                    }
                }
            }
        }
        return "http://127.0.0.1:$port/$key"
    }

    fun putContent(httpContent: HttpContent): String {
        val key = System.nanoTime().toString(16)
        cachedData[key] = httpContent
        return "http://127.0.0.1:$port/$key"
    }

    fun removeUrl(url: String) {
        val key = url.substringAfterLast('/')
        cachedData.remove(key)
    }

    private fun handleRequest(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val outputStream = socket.getOutputStream()
        val writer = PrintWriter(outputStream)
        val line = reader.readLine() ?: return
        fun close() {
            runCatching {
                reader.close()
                writer.close()
                outputStream.close()
                socket.close()
            }.onFailure {
                AbstractLogger.directError("failed to close socket", it)
            }
        }
        val parse = StringTokenizer(line)
        val method = parse.nextToken().uppercase(Locale.getDefault())
        var fileRequested = parse.nextToken().lowercase(Locale.getDefault())
        AbstractLogger.directDebug("[http-server:${port}] $method $fileRequested")

        if (method != "GET") {
            with(writer) {
                println("HTTP/1.1 501 Not Implemented")
                println("Content-type: " + "application/octet-stream")
                println("Content-length: " + 0)
                println()
                flush()
            }
            close()
            return
        }
        if (fileRequested.startsWith("/")) {
            fileRequested = fileRequested.substring(1)
        }
        val requestedData = cachedData[fileRequested] ?: writer.run {
            println("HTTP/1.1 404 Not Found")
            println("Content-type: " + "application/octet-stream")
            println("Content-length: " + 0)
            println()
            flush()
            close()
            return
        }
        with(writer) {
            println("HTTP/1.1 200 OK")
            println("Content-type: " + "application/octet-stream")
            if (requestedData.chunked) println("Transfer-encoding: chunked")
            else println("Content-length: " + requestedData.contentLength)
            println()
            flush()
        }

        val responseBody = requestedData.newBody()
        responseBody.onOpen()
        try {
            if (requestedData.chunked) {
                val buffer = ByteArray(32768)
                while (true) {
                    val read = responseBody.readBytes(buffer)
                    if (read == -1) break
                    outputStream.write(Integer.toHexString(read).toByteArray())
                    outputStream.write("\r\n".toByteArray())
                    outputStream.write(buffer, 0, read)
                    outputStream.write("\r\n".toByteArray())
                    outputStream.flush()
                }
            } else {
                cachedData.remove(fileRequested)
                val buffer = ByteArray(4096)
                while (true) {
                    val read = responseBody.readBytes(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    outputStream.flush()
                }
            }
        } catch (t: Throwable) {
            AbstractLogger.directDebug("failed to write to socket ${t.localizedMessage}")
        } finally {
            if (requestedData.chunked) runCatching { outputStream.write("0\r\n\r\n".toByteArray()) }
            responseBody.onClose()
        }
        outputStream.flush()
        close()
    }
}