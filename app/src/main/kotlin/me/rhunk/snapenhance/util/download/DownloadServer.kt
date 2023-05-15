package me.rhunk.snapenhance.util.download

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.Logger.debug
import me.rhunk.snapenhance.ModContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer

class DownloadServer(
    private val context: ModContext
) {
    private val port = ThreadLocalRandom.current().nextInt(10000, 65535)

    private val cachedData = ConcurrentHashMap<String, ByteArray>()
    private var serverSocket: ServerSocket? = null

    fun startFileDownload(destination: File, content: ByteArray, callback: Consumer<Boolean>) {
        val httpKey = java.lang.Long.toHexString(System.nanoTime())
        ensureServerStarted {
            putDownloadableContent(httpKey, content)
            val url = "http://127.0.0.1:$port/$httpKey"
            context.executeAsync {
                val result: Boolean = context.bridgeClient.downloadContent(url, destination.absolutePath)
                callback.accept(result)
            }
        }
    }

    private fun ensureServerStarted(callback: Runnable) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            callback.run()
            return
        }
        Thread {
            try {
                debug("started web server on 127.0.0.1:$port")
                serverSocket = ServerSocket(port)
                callback.run()
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

    fun putDownloadableContent(key: String, data: ByteArray) {
        cachedData[key] = data
    }

    private fun handleRequest(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val outputStream = socket.getOutputStream()
        val writer = PrintWriter(outputStream)
        val line = reader.readLine() ?: return
        val close = Runnable {
            try {
                reader.close()
                writer.close()
                outputStream.close()
                socket.close()
            } catch (e: Throwable) {
                Logger.xposedLog(e)
            }
        }
        val parse = StringTokenizer(line)
        val method = parse.nextToken().uppercase(Locale.getDefault())
        var fileRequested = parse.nextToken().lowercase(Locale.getDefault())
        if (method != "GET") {
            writer.println("HTTP/1.1 501 Not Implemented")
            writer.println("Content-type: " + "application/octet-stream")
            writer.println("Content-length: " + 0)
            writer.println()
            writer.flush()
            close.run()
            return
        }
        if (fileRequested.startsWith("/")) {
            fileRequested = fileRequested.substring(1)
        }
        if (!cachedData.containsKey(fileRequested)) {
            writer.println("HTTP/1.1 404 Not Found")
            writer.println("Content-type: " + "application/octet-stream")
            writer.println("Content-length: " + 0)
            writer.println()
            writer.flush()
            close.run()
            return
        }
        val data = cachedData[fileRequested]!!
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-type: " + "application/octet-stream")
        writer.println("Content-length: " + data.size)
        writer.println()
        writer.flush()
        outputStream.write(data, 0, data.size)
        outputStream.flush()
        close.run()
        cachedData.remove(fileRequested)
    }
}