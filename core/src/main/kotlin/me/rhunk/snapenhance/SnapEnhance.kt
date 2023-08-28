package me.rhunk.snapenhance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.SyncCallback
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.core.eventbus.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.ktx.getApplicationInfoCompat
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class SnapEnhance {
    companion object {
        lateinit var classLoader: ClassLoader
        val classCache: SnapClassCache by lazy {
            SnapClassCache(classLoader)
        }
    }
    private val appContext = ModContext()
    private var isBridgeInitialized = false

    init {
        Hooker.hook(Application::class.java, "attach", HookStage.BEFORE) { param ->
            appContext.androidContext = param.arg<Context>(0).also {
                classLoader = it.classLoader
            }
            appContext.bridgeClient = BridgeClient(appContext)

            //for lspatch builds, we need to check if the service is correctly installed
            runCatching {
                appContext.androidContext.packageManager.getApplicationInfoCompat(BuildConfig.APPLICATION_ID, PackageManager.GET_META_DATA)
            }.onFailure {
                appContext.crash("SnapEnhance bridge service is not installed. Please download stable version from https://github.com/rhunk/SnapEnhance/releases")
                return@hook
            }

            appContext.bridgeClient.apply {
                start { bridgeResult ->
                    if (!bridgeResult) {
                        Logger.xposedLog("Cannot connect to bridge service")
                        appContext.softRestartApp()
                        return@start
                    }
                    runCatching {
                        runBlocking {
                            init()
                        }
                    }.onSuccess {
                        isBridgeInitialized = true
                    }.onFailure {
                        Logger.xposedLog("Failed to initialize", it)
                    }
                }
            }
        }

        Activity::class.java.hook( "onCreate",  HookStage.AFTER, { isBridgeInitialized }) {
            val activity = it.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            val isMainActivityNotNull = appContext.mainActivity != null
            appContext.mainActivity = activity
            if (isMainActivityNotNull || !appContext.mappings.isMappingsLoaded()) return@hook
            onActivityCreate()
        }

        var activityWasResumed = false

        //we need to reload the config when the app is resumed
        //FIXME: called twice at first launch
        Activity::class.java.hook("onResume", HookStage.AFTER, { isBridgeInitialized }) {
            val activity = it.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook

            if (!activityWasResumed) {
                activityWasResumed = true
                return@hook
            }

            Logger.debug("Reloading config")
            appContext.reloadConfig()
            syncRemote()
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun init() {
        measureTime {
            with(appContext) {
                reloadConfig()
                initNative()
                withContext(appContext.coroutineDispatcher) {
                    translation.userLocale = getConfigLocale()
                    translation.loadFromBridge(bridgeClient)
                }

                mappings.loadFromBridge(bridgeClient)
                mappings.init(androidContext)
                eventDispatcher.init()
                //if mappings aren't loaded, we can't initialize features
                if (!mappings.isMappingsLoaded()) return
                features.init()
                syncRemote()
            }
        }.also { time ->
            Logger.debug("init took $time")
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun onActivityCreate() {
        measureTime {
            with(appContext) {
                features.onActivityCreate()
                actionManager.init()
            }
        }.also { time ->
            Logger.debug("onActivityCreate took $time")
        }
    }

    private fun initNative() {
        // don't initialize native when not logged in
        if (!appContext.database.hasArroyo()) return
        appContext.native.apply {
            if (appContext.config.experimental.nativeHooks.globalState != true) return@apply
            initOnce(appContext.androidContext.classLoader)
            nativeUnaryCallCallback = { request ->
                appContext.event.post(UnaryCallEvent(request.uri, request.buffer))?.also {
                    request.buffer = it.buffer
                    request.canceled = it.canceled
                }
            }
        }
    }

    private fun syncRemote() {
        val database = appContext.database

        appContext.executeAsync {
            Logger.debug("request remote sync")
            appContext.bridgeClient.sync(object : SyncCallback.Stub() {
                override fun syncFriend(uuid: String): String? {
                    return database.getFriendInfo(uuid)?.toJson()
                }

                override fun syncGroup(uuid: String): String? {
                    return database.getFeedEntryByConversationId(uuid)?.let {
                        MessagingGroupInfo(
                            it.key!!,
                            it.feedDisplayName!!,
                            it.participantsSize
                        ).toJson()
                    }
                }
            })

            appContext.event.subscribe(SnapWidgetBroadcastReceiveEvent::class) { event ->
                if (event.action != BridgeClient.BRIDGE_SYNC_ACTION) return@subscribe
                event.canceled = true
                val feedEntries = appContext.database.getFeedEntries(Int.MAX_VALUE)

                val groups = feedEntries.filter { it.friendUserId == null }.map {
                    MessagingGroupInfo(
                        it.key!!,
                        it.feedDisplayName!!,
                        it.participantsSize
                    )
                }

                val friends = feedEntries.filter { it.friendUserId != null }.map {
                    MessagingFriendInfo(
                        it.friendUserId!!,
                        it.friendDisplayName,
                        it.friendDisplayUsername!!.split("|")[1],
                        it.bitmojiAvatarId,
                        it.bitmojiSelfieId
                    )
                }

                appContext.bridgeClient.passGroupsAndFriends(
                    groups.map { it.toJson() },
                    friends.map { it.toJson() }
                )
            }
        }
    }
}