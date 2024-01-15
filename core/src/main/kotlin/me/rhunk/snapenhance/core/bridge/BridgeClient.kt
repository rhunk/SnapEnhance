package me.rhunk.snapenhance.core.bridge


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.bridge.BridgeInterface
import me.rhunk.snapenhance.bridge.ConfigStateListener
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.bridge.MessageLoggerInterface
import me.rhunk.snapenhance.bridge.SyncCallback
import me.rhunk.snapenhance.bridge.e2ee.E2eeInterface
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.common.bridge.types.FileActionType
import me.rhunk.snapenhance.common.bridge.types.LocalePair
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.toSerialized
import me.rhunk.snapenhance.core.ModContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun FileLoaderWrapper.loadFromBridge(bridgeClient: BridgeClient) {
    isFileExists = { bridgeClient.isFileExists(fileType) }
    read = { bridgeClient.createAndReadFile(fileType, defaultContent) }
    write = { bridgeClient.writeFile(fileType, it) }
    delete = { bridgeClient.deleteFile(fileType) }
}


class BridgeClient(
    private val context: ModContext
):  ServiceConnection {
    private lateinit var future: CompletableFuture<Boolean>
    private lateinit var service: BridgeInterface

    fun connect(onFailure: (Throwable) -> Unit, onResult: (Boolean) -> Unit) {
        this.future = CompletableFuture()

        with(context.androidContext) {
            runCatching {
                startActivity(Intent()
                    .setClassName(Constants.SE_PACKAGE_NAME, "me.rhunk.snapenhance.bridge.ForceStartActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                )
            }

            //ensure the remote process is running
            runCatching {
                val intent = Intent()
                    .setClassName(Constants.SE_PACKAGE_NAME,"me.rhunk.snapenhance.bridge.BridgeService")
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
                onResult(future.get(15, TimeUnit.SECONDS))
            }.onFailure {
                onFailure(it)
            }
        }
    }


    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        this.service = BridgeInterface.Stub.asInterface(service)
        future.complete(true)
    }

    override fun onNullBinding(name: ComponentName) {
        context.log.error("BridgeClient", "failed to connect to bridge service")
        exitProcess(1)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        exitProcess(0)
    }

    fun broadcastLog(tag: String, level: String, message: String) = service.broadcastLog(tag, level, message)

    //TODO: use interfaces instead of direct file access
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

    fun fetchLocales(userLocale: String) = service.fetchLocales(userLocale).map {
        LocalePair(it.key, it.value)
    }

    fun getApplicationApkPath(): String = service.getApplicationApkPath()

    fun enqueueDownload(intent: Intent, callback: DownloadCallback) = service.enqueueDownload(intent, callback)

    fun sync(callback: SyncCallback) {
        if (!context.database.hasMain()) return
        service.sync(callback)
    }

    fun triggerSync(scope: SocialScope, id: String) = service.triggerSync(scope.key, id)

    fun passGroupsAndFriends(groups: List<MessagingGroupInfo>, friends: List<MessagingFriendInfo>) = service.passGroupsAndFriends(
        groups.mapNotNull { it.toSerialized() },
        friends.mapNotNull { it.toSerialized() }
    )

    fun getRules(targetUuid: String): List<MessagingRuleType> {
        return service.getRules(targetUuid).mapNotNull { MessagingRuleType.getByName(it) }
    }

    fun getRuleIds(ruleType: MessagingRuleType): List<String> {
        return service.getRuleIds(ruleType.key)
    }

    fun setRule(targetUuid: String, type: MessagingRuleType, state: Boolean)
        = service.setRule(targetUuid, type.key, state)

    fun getScriptingInterface(): IScripting = service.getScriptingInterface()

    fun getE2eeInterface(): E2eeInterface = service.getE2eeInterface()

    fun getMessageLogger(): MessageLoggerInterface = service.messageLogger

    fun registerMessagingBridge(bridge: MessagingBridge) = service.registerMessagingBridge(bridge)

    fun openSettingsOverlay() = service.openSettingsOverlay()
    fun closeSettingsOverlay() = service.closeSettingsOverlay()

    fun registerConfigStateListener(listener: ConfigStateListener) = service.registerConfigStateListener(listener)
}
