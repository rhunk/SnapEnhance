package me.rhunk.snapenhance.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import me.rhunk.snapenhance.core.LogLevel
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.core.bridge.types.BridgeFileType
import me.rhunk.snapenhance.core.bridge.types.FileActionType
import me.rhunk.snapenhance.core.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.bridge.wrapper.MessageLoggerWrapper
import me.rhunk.snapenhance.core.database.objects.FriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.download.DownloadProcessor
import me.rhunk.snapenhance.core.util.SerializableDataObject
import kotlin.system.measureTimeMillis

class BridgeService : Service() {
    private lateinit var messageLoggerWrapper: MessageLoggerWrapper
    private lateinit var remoteSideContext: RemoteSideContext
    lateinit var syncCallback: SyncCallback

    override fun onBind(intent: Intent): IBinder? {
        remoteSideContext = SharedContextHolder.remote(this).apply {
            if (checkForRequirements()) return null
        }
        remoteSideContext.apply {
            bridgeService = this@BridgeService
        }
        messageLoggerWrapper = MessageLoggerWrapper(getDatabasePath(BridgeFileType.MESSAGE_LOGGER_DATABASE.fileName)).also { it.init() }
        return BridgeBinder()
    }

    fun triggerFriendSync(friendId: String) {
        val syncedFriend = syncCallback.syncFriend(friendId)
        if (syncedFriend == null) {
            remoteSideContext.log.error("Failed to sync friend $friendId")
            return
        }
        SerializableDataObject.fromJson<FriendInfo>(syncedFriend).let {
            remoteSideContext.modDatabase.syncFriend(it)
        }
    }

    fun triggerGroupSync(groupId: String) {
        val syncedGroup = syncCallback.syncGroup(groupId)
        if (syncedGroup == null) {
            remoteSideContext.log.error("Failed to sync group $groupId")
            return
        }
        SerializableDataObject.fromJson<MessagingGroupInfo>(syncedGroup).let {
            remoteSideContext.modDatabase.syncGroupInfo(it)
        }
    }

    inner class BridgeBinder : BridgeInterface.Stub() {
        override fun broadcastLog(tag: String, level: String, message: String) {
            remoteSideContext.log.internalLog(tag, LogLevel.fromShortName(level) ?: LogLevel.INFO, message)
        }

        override fun fileOperation(action: Int, fileType: Int, content: ByteArray?): ByteArray {
            val resolvedFile = BridgeFileType.fromValue(fileType)?.resolve(this@BridgeService)

            return when (FileActionType.values()[action]) {
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

        override fun getLoggedMessageIds(conversationId: String, limit: Int) =
            messageLoggerWrapper.getMessageIds(conversationId, limit).toLongArray()

        override fun getMessageLoggerMessage(conversationId: String, id: Long) =
            messageLoggerWrapper.getMessage(conversationId, id).second

        override fun addMessageLoggerMessage(conversationId: String, id: Long, message: ByteArray) {
            messageLoggerWrapper.addMessage(conversationId, id, message)
        }

        override fun deleteMessageLoggerMessage(conversationId: String, id: Long) =
            messageLoggerWrapper.deleteMessage(conversationId, id)

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
                    runCatching {
                        triggerFriendSync(friendId)
                    }.onFailure {
                        remoteSideContext.log.error("Failed to sync friend $friendId", it)
                    }
                }
                remoteSideContext.modDatabase.getGroups().map { it.conversationId }.forEach { groupId ->
                    runCatching {
                        triggerGroupSync(groupId)
                    }.onFailure {
                        remoteSideContext.log.error("Failed to sync group $groupId", it)
                    }
                }
            }.also {
                remoteSideContext.log.verbose("Syncing remote took $it ms")
            }
        }

        override fun passGroupsAndFriends(
            groups: List<String>,
            friends: List<String>
        ) {
            remoteSideContext.log.verbose("Received ${groups.size} groups and ${friends.size} friends")
            remoteSideContext.modDatabase.receiveMessagingDataCallback(
                friends.map { SerializableDataObject.fromJson<MessagingFriendInfo>(it) },
                groups.map { SerializableDataObject.fromJson<MessagingGroupInfo>(it) }
            )
        }
    }
}
