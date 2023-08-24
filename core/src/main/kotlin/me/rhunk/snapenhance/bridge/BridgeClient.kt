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
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.bridge.types.FileActionType
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.data.LocalePair
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.system.exitProcess


class BridgeClient(
    private val context: ModContext
):  ServiceConnection {
    private lateinit var future: CompletableFuture<Boolean>
    private lateinit var service: BridgeInterface

    companion object {
        const val BRIDGE_SYNC_ACTION = "me.rhunk.snapenhance.bridge.SYNC"
    }

    fun start(callback: (Boolean) -> Unit) {
        this.future = CompletableFuture()

        //TODO: randomize package name
        with(context.androidContext) {
            //ensure the remote process is running
            startActivity(Intent()
                .setClassName(BuildConfig.APPLICATION_ID, "me.rhunk.snapenhance.bridge.ForceStartActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            )

            val intent = Intent()
                .setClassName(BuildConfig.APPLICATION_ID, "me.rhunk.snapenhance.bridge.BridgeService")
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
        exitProcess(1)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        exitProcess(0)
    }

    fun createAndReadFile(
        fileType: BridgeFileType,
        defaultContent: ByteArray
    ): ByteArray = service.fileOperation(FileActionType.CREATE_AND_READ.ordinal, fileType.value, defaultContent)

    fun readFile(fileType: BridgeFileType): ByteArray = service.fileOperation(FileActionType.READ.ordinal, fileType.value, null)

    fun writeFile(
        fileType: BridgeFileType,
        content: ByteArray?
    ) { service.fileOperation(FileActionType.WRITE.ordinal, fileType.value, content) }

    fun deleteFile(fileType: BridgeFileType) { service.fileOperation(FileActionType.DELETE.ordinal, fileType.value, null) }

    fun isFileExists(fileType: BridgeFileType) = service.fileOperation(FileActionType.EXISTS.ordinal, fileType.value, null).isNotEmpty()

    fun getLoggedMessageIds(conversationId: String, limit: Int): LongArray = service.getLoggedMessageIds(conversationId, limit)

    fun getMessageLoggerMessage(conversationId: String, id: Long): ByteArray? = service.getMessageLoggerMessage(conversationId, id)

    fun addMessageLoggerMessage(conversationId: String, id: Long, message: ByteArray) = service.addMessageLoggerMessage(conversationId, id, message)

    fun deleteMessageLoggerMessage(conversationId: String, id: Long) = service.deleteMessageLoggerMessage(conversationId, id)

    fun clearMessageLogger() = service.clearMessageLogger()

    fun fetchLocales(userLocale: String) = service.fetchLocales(userLocale).map {
        LocalePair(it.key, it.value)
    }

    fun getApplicationApkPath() = service.getApplicationApkPath()

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

    fun sync(callback: SyncCallback) = service.sync(callback)

    fun passGroupsAndFriends(groups: List<String>, friends: List<String>) = service.passGroupsAndFriends(groups, friends)

    fun getRules(targetUuid: String): List<MessagingRuleType> {
        return service.getRules(targetUuid).map { MessagingRuleType.getByName(it) }
    }

    fun setRule(targetUuid: String, type: MessagingRuleType, state: Boolean)
        = service.setRule(targetUuid, type.key, state)
}
