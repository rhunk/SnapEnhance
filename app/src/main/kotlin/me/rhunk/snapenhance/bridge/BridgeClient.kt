package me.rhunk.snapenhance.bridge


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.data.LocalePair
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.system.exitProcess


class BridgeClient(
    private val context: ModContext
):  ServiceConnection {
    private lateinit var future: CompletableFuture<Boolean>
    private lateinit var service: BridgeInterface

    fun start(callback: (Boolean) -> Unit) {
        this.future = CompletableFuture()

        with(context.androidContext) {
            //ensure the remote process is running
            startActivity(Intent()
                .setClassName(BuildConfig.APPLICATION_ID, ForceStartActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            )

            val intent = Intent()
                .setClassName(BuildConfig.APPLICATION_ID, BridgeService::class.java.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bindService(
                    intent,
                    Context.BIND_AUTO_CREATE,
                    Executors.newSingleThreadExecutor(),
                    this@BridgeClient
                )
            } else {
                XposedHelpers.callMethod(
                    this,
                    "bindServiceAsUser",
                    intent,
                    this@BridgeClient,
                    Context.BIND_AUTO_CREATE,
                    Handler(HandlerThread("BridgeClient").apply {
                        start()
                    }.looper),
                    android.os.Process.myUserHandle()
                )
            }
        }
        callback(future.get())
    }


    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        this.service = BridgeInterface.Stub.asInterface(service)
        future.complete(true)
    }

    override fun onNullBinding(name: ComponentName) {
        xposedLog("failed to connect to bridge service")
        future.complete(false)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        exitProcess(0)
    }

    fun createAndReadFile(
        fileType: BridgeFileType,
        defaultContent: ByteArray
    ): ByteArray = service.createAndReadFile(fileType.value, defaultContent)

    fun readFile(fileType: BridgeFileType): ByteArray = service.readFile(fileType.value)

    fun writeFile(
        fileType: BridgeFileType,
        content: ByteArray?
    ): Boolean = service.writeFile(fileType.value, content)

    fun deleteFile(fileType: BridgeFileType) = service.deleteFile(fileType.value)


    fun isFileExists(fileType: BridgeFileType) = service.isFileExists(fileType.value)

    fun getLoggedMessageIds(conversationId: String, limit: Int): LongArray = service.getLoggedMessageIds(conversationId, limit)

    fun getMessageLoggerMessage(conversationId: String, id: Long): ByteArray? = service.getMessageLoggerMessage(conversationId, id)

    fun addMessageLoggerMessage(conversationId: String,id: Long, message: ByteArray) = service.addMessageLoggerMessage(conversationId, id, message)

    fun deleteMessageLoggerMessage(conversationId: String, id: Long) = service.deleteMessageLoggerMessage(conversationId, id)

    fun clearMessageLogger() = service.clearMessageLogger()

    fun fetchTranslations() = service.fetchTranslations().map {
        LocalePair(it.key, it.value)
    }

    fun getAutoUpdaterTime(): Long {
        createAndReadFile(BridgeFileType.AUTO_UPDATER_TIMESTAMP, "0".toByteArray()).run {
            return if (isEmpty()) {
                0
            } else {
                String(this).toLong()
            }
        }
    }

    fun setAutoUpdaterTime(time: Long) {
        writeFile(BridgeFileType.AUTO_UPDATER_TIMESTAMP, time.toString().toByteArray())
    }

    fun enqueueDownload(intent: Intent, callback: DownloadCallback) = service.enqueueDownload(intent, callback)
}
