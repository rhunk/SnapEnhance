package me.rhunk.snapenhance.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.common.bridge.types.FileActionType
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.logger.LogLevel
import me.rhunk.snapenhance.common.util.toParcelable
import me.rhunk.snapenhance.download.DownloadProcessor
import me.rhunk.snapenhance.download.FFMpegProcessor
import me.rhunk.snapenhance.task.Task
import me.rhunk.snapenhance.task.TaskType
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

class BridgeService : Service() {
    private lateinit var remoteSideContext: RemoteSideContext
    lateinit var syncCallback: SyncCallback
    var messagingBridge: MessagingBridge? = null

    override fun onDestroy() {
        if (::remoteSideContext.isInitialized) {
            remoteSideContext.bridgeService = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        remoteSideContext = SharedContextHolder.remote(this).apply {
            if (checkForRequirements()) return null
        }
        remoteSideContext.apply {
            bridgeService = this@BridgeService
        }
        return BridgeBinder()
    }

    fun triggerScopeSync(scope: SocialScope, id: String, updateOnly: Boolean = false) {
        runCatching {
            val modDatabase = remoteSideContext.modDatabase
            val syncedObject = when (scope) {
                SocialScope.FRIEND -> {
                    if (updateOnly && modDatabase.getFriendInfo(id) == null) return
                    syncCallback.syncFriend(id)
                }
                SocialScope.GROUP -> {
                    if (updateOnly && modDatabase.getGroupInfo(id) == null) return
                    syncCallback.syncGroup(id)
                }
                else -> null
            }

            if (syncedObject == null) {
                remoteSideContext.log.warn("Failed to sync $scope $id")
                return
            }

            when (scope) {
                SocialScope.FRIEND -> {
                    toParcelable<MessagingFriendInfo>(syncedObject)?.let {
                        modDatabase.syncFriend(it)
                    }
                }
                SocialScope.GROUP -> {
                    toParcelable<MessagingGroupInfo>(syncedObject)?.let {
                        modDatabase.syncGroupInfo(it)
                    }
                }
            }
        }.onFailure {
            remoteSideContext.log.error("Failed to sync $scope $id", it)
        }
    }

    inner class BridgeBinder : BridgeInterface.Stub() {
        override fun broadcastLog(tag: String, level: String, message: String) {
            remoteSideContext.log.internalLog(tag, LogLevel.fromShortName(level) ?: LogLevel.INFO, message)
        }

        override fun fileOperation(action: Int, fileType: Int, content: ByteArray?): ByteArray {
            val resolvedFile = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)

            return when (FileActionType.entries[action]) {
                FileActionType.CREATE_AND_READ -> {
                    resolvedFile?.let {
                        if (!it.exists()) {
                            return content?.also { content -> it.writeBytes(content) } ?: ByteArray(
                                0
                            )
                        }

                        it.readBytes()
                    } ?: ByteArray(0)
                }

                FileActionType.READ -> {
                    resolvedFile?.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
                }

                FileActionType.WRITE -> {
                    content?.also { resolvedFile?.writeBytes(content) } ?: ByteArray(0)
                }

                FileActionType.DELETE -> {
                    resolvedFile?.takeIf { it.exists() }?.delete()
                    ByteArray(0)
                }

                FileActionType.EXISTS -> {
                    if (resolvedFile?.exists() == true)
                        ByteArray(1)
                    else ByteArray(0)
                }
            }
        }

        override fun getApplicationApkPath(): String = applicationInfo.publicSourceDir

        override fun fetchLocales(userLocale: String) =
            LocaleWrapper.fetchLocales(context = this@BridgeService, userLocale).associate {
                it.locale to it.content
            }

        override fun enqueueDownload(intent: Intent, callback: DownloadCallback) {
            DownloadProcessor(
                remoteSideContext = remoteSideContext,
                callback = callback
            ).onReceive(intent)
        }

        override fun convertMedia(
            input: ParcelFileDescriptor?,
            inputExtension: String,
            outputExtension: String,
            audioCodec: String?,
            videoCodec: String?
        ): ParcelFileDescriptor? {
            return runBlocking {
                val taskId = UUID.randomUUID().toString()
                val inputFile = File.createTempFile(taskId, ".$inputExtension", remoteSideContext.androidContext.cacheDir)

                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(input).use { inputStream ->
                        inputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }.onFailure {
                    remoteSideContext.log.error("Failed to copy input file", it)
                    inputFile.delete()
                    return@runBlocking null
                }
                val cachedFile = File.createTempFile(taskId, ".$outputExtension", remoteSideContext.androidContext.cacheDir)

                val pendingTask = remoteSideContext.taskManager.createPendingTask(
                    Task(
                        type = TaskType.DOWNLOAD,
                        title = "Media conversion",
                        author = null,
                        hash = taskId
                    )
                )
                runCatching {
                    FFMpegProcessor.newFFMpegProcessor(remoteSideContext, pendingTask).execute(
                        FFMpegProcessor.Request(
                            action = FFMpegProcessor.Action.CONVERSION,
                            inputs = listOf(inputFile.absolutePath),
                            output = cachedFile,
                            videoCodec = videoCodec,
                            audioCodec = audioCodec
                        )
                    )
                    pendingTask.success()
                    return@runBlocking ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }.onFailure {
                    pendingTask.fail(it.message ?: "Failed to convert video")
                    remoteSideContext.log.error("Failed to convert video", it)
                }

                inputFile.delete()
                cachedFile.delete()
                null
            }
        }

        override fun getRules(uuid: String): List<String> {
            return remoteSideContext.modDatabase.getRules(uuid).map { it.key }
        }

        override fun getRuleIds(type: String): MutableList<String> {
            return remoteSideContext.modDatabase.getRuleIds(type)
        }

        override fun setRule(uuid: String, rule: String, state: Boolean) {
            remoteSideContext.modDatabase.setRule(uuid, rule, state)
        }

        override fun sync(callback: SyncCallback) {
            syncCallback = callback
            measureTimeMillis {
                remoteSideContext.modDatabase.getFriends().map { it.userId } .forEach { friendId ->
                    triggerScopeSync(SocialScope.FRIEND, friendId, true)
                }
                remoteSideContext.modDatabase.getGroups().map { it.conversationId }.forEach { groupId ->
                    triggerScopeSync(SocialScope.GROUP, groupId, true)
                }
            }.also {
                remoteSideContext.log.verbose("Syncing remote took $it ms")
            }
        }

        override fun triggerSync(scope: String, id: String) {
            remoteSideContext.log.verbose("trigger sync for $scope $id")
            triggerScopeSync(SocialScope.getByName(scope), id, true)
        }

        override fun passGroupsAndFriends(
            groups: List<String>,
            friends: List<String>
        ) {
            remoteSideContext.log.verbose("Received ${groups.size} groups and ${friends.size} friends")
            remoteSideContext.modDatabase.receiveMessagingDataCallback(
                friends.mapNotNull { toParcelable<MessagingFriendInfo>(it) },
                groups.mapNotNull { toParcelable<MessagingGroupInfo>(it) }
            )
        }

        override fun getScriptingInterface() = remoteSideContext.scriptManager

        override fun getE2eeInterface() = remoteSideContext.e2eeImplementation
        override fun getLogger() = remoteSideContext.messageLogger
        override fun getTracker() = remoteSideContext.tracker
        override fun getAccountStorage() = remoteSideContext.accountStorage
        override fun registerMessagingBridge(bridge: MessagingBridge) {
            messagingBridge = bridge
        }

        override fun openSettingsOverlay() {
            runCatching {
                remoteSideContext.settingsOverlay.show()
            }.onFailure {
                remoteSideContext.log.error("Failed to open settings overlay", it)
            }
        }

        override fun closeSettingsOverlay() {
            runCatching {
                remoteSideContext.settingsOverlay.close()
            }.onFailure {
                remoteSideContext.log.error("Failed to close settings overlay", it)
            }
        }

        override fun registerConfigStateListener(listener: ConfigStateListener) {
            remoteSideContext.config.configStateListener = listener
        }

        override fun getDebugProp(key: String, defaultValue: String?): String? {
            return remoteSideContext.sharedPreferences.all["debug_$key"]?.toString() ?: defaultValue
        }
    }
}
