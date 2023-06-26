package me.rhunk.snapenhance.util.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.Logger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.StringTokenizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class DownloadServer(
    private val timeout: Int = 10000
) {
    private val port = ThreadLocalRandom.current().nextInt(10000, 65535)

    private val cachedData = ConcurrentHashMap<String, Pair<InputStream, Long>>()
    private var serverSocket: ServerSocket? = null

    fun ensureServerStarted(callback: DownloadServer.() -> Unit) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            callback(this)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            Logger.debug("starting download server on port $port")
            serverSocket = ServerSocket(port)
            serverSocket!!.soTimeout = timeout
            callback(this@DownloadServer)
            while (!serverSocket!!.isClosed) {
                try {
                    val socket = serverSocket!!.accept()
                    launch(Dispatchers.IO) {
                        handleRequest(socket)
                    }
                } catch (e: SocketTimeoutException) {
                    serverSocket?.close()
                    serverSocket = null
                    Logger.debug("download server closed")
                    break;
                } catch (e: Exception) {
                    Logger.error("failed to handle request", e)
                }
            }
        }
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