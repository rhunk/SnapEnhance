package me.rhunk.snapenhance.util.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Logger
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
import kotlin.random.Random

class HttpServer(
    private val timeout: Int = 10000
) {
    val port = Random.nextInt(10000, 65535)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var timeoutJob: Job? = null
    private var socketJob: Job? = null

    private val cachedData = ConcurrentHashMap<String, Pair<InputStream, Long>>()
    private var serverSocket: ServerSocket? = null

    fun ensureServerStarted(callback: HttpServer.() -> Unit) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            callback(this)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            Logger.debug("starting http server on port $port")
            serverSocket = ServerSocket(port)
            callback(this@HttpServer)
            while (!serverSocket!!.isClosed) {
                try {
                    val socket = serverSocket!!.accept()
                    timeoutJob?.cancel()
                    launch {
                        handleRequest(socket)
                        timeoutJob = launch {
                            delay(timeout.toLong())
                            Logger.debug("http server closed due to timeout")
                            runCatching {
                                socketJob?.cancel()
                                socket.close()
                                serverSocket?.close()
                            }.onFailure {
                                Logger.error(it)
                            }
                        }
                    }
                } catch (e: SocketException) {
                    Logger.debug("http server timed out")
                    break;
                } catch (e: Throwable) {
                    Logger.error("failed to handle request", e)
                }
            }
        }.also { socketJob = it }
    }

    fun close() {
        serverSocket?.close()
    }

    fun putDownloadableContent(inputStream: InputStream, size: Long): String {
        val key = System.nanoTime().toString(16)
        cachedData[key] = inputStream to size
        return "http://127.0.0.1:$port/$key"
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
                Logger.error("failed to close socket", it)
            }
        }
        val parse = StringTokenizer(line)
        val method = parse.nextToken().uppercase(Locale.getDefault())
        var fileRequested = parse.nextToken().lowercase(Locale.getDefault())
        Logger.debug("[http-server:${port}] $method $fileRequested")

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
        if (!cachedData.containsKey(fileRequested)) {
            with(writer) {
                println("HTTP/1.1 404 Not Found")
                println("Content-type: " + "application/octet-stream")
                println("Content-length: " + 0)
                println()
                flush()
            }
            close()
            return
        }
        val requestedData = cachedData[fileRequested]!!
        with(writer) {
            println("HTTP/1.1 200 OK")
            println("Content-type: " + "application/octet-stream")
            println("Content-length: " + requestedData.second)
            println()
            flush()
        }
        requestedData.first.copyTo(outputStream)
        outputStream.flush()
        cachedData.remove(fileRequested)
        close()
    }
}