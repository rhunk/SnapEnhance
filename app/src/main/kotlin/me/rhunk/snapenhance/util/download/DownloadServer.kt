package me.rhunk.snapenhance.util.download

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.Logger.debug
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.StringTokenizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class DownloadServer {
    private val port = ThreadLocalRandom.current().nextInt(10000, 65535)

    private val cachedData = ConcurrentHashMap<String, InputStream>()
    private var serverSocket: ServerSocket? = null

    fun ensureServerStarted(callback: DownloadServer.() -> Unit) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            callback(this)
            return
        }
        Thread {
            try {
                debug("started web server on 127.0.0.1:$port")
                serverSocket = ServerSocket(port)
                callback(this)
                while (!serverSocket!!.isClosed) {
                    try {
                        val socket = serverSocket!!.accept()
                        Thread { handleRequest(socket) }.start()
                    } catch (e: Throwable) {
                        Logger.xposedLog(e)
                    }
                }
            } catch (e: Throwable) {
                Logger.xposedLog(e)
            }
        }.start()
    }

    fun putDownloadableContent(inputStream: InputStream): String {
        val key = System.nanoTime().toString(16)
        cachedData[key] = inputStream
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
            println()
            flush()
        }
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (requestedData.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.flush()
        cachedData.remove(fileRequested)
        close()
    }
}