package me.rhunk.snapenhance

import android.app.Activity
import android.app.Application
import android.content.Context
import me.rhunk.snapenhance.bridge.SyncCallback
import me.rhunk.snapenhance.core.Logger
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.event.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


class SnapEnhance {
    companion object {
        lateinit var classLoader: ClassLoader
            private set
        val classCache by lazy {
            SnapClassCache(classLoader)
        }
    }
    private val appContext = ModContext()
    private var isBridgeInitialized = false

    private fun hookMainActivity(methodName: String, stage: HookStage = HookStage.AFTER, block: Activity.() -> Unit) {
        Activity::class.java.hook(methodName, stage, { isBridgeInitialized }) { param ->
            val activity = param.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            block(activity)
        }
    }

    init {
        Application::class.java.hook("attach", HookStage.BEFORE) { param ->
            appContext.apply {
                androidContext = param.arg<Context>(0).also {
                    classLoader = it.classLoader
                }
                bridgeClient = BridgeClient(appContext)
                bridgeClient.apply {
                    connect(
                        timeout = {
                            crash("SnapEnhance bridge service is not responding. Please download stable version from https://github.com/rhunk/SnapEnhance/releases", it)
                        }
                    ) { bridgeResult ->
                        if (!bridgeResult) {
                            Logger.xposedLog("Cannot connect to bridge service")
                            softRestartApp()
                            return@connect
                        }
                        runCatching {
                            init()
                        }.onSuccess {
                            isBridgeInitialized = true
                        }.onFailure {
                            Logger.xposedLog("Failed to initialize", it)
                        }
                    }
                }
            }
        }

        hookMainActivity("onCreate") {
            val isMainActivityNotNull = appContext.mainActivity != null
            appContext.mainActivity = this
            if (isMainActivityNotNull || !appContext.mappings.isMappingsLoaded()) return@hookMainActivity
            onActivityCreate()
        }

        hookMainActivity("onPause") {
            appContext.bridgeClient.closeSettingsOverlay()
        }

        var activityWasResumed = false
        //we need to reload the config when the app is resumed
        //FIXME: called twice at first launch
        hookMainActivity("onResume") {
            if (!activityWasResumed) {
                activityWasResumed = true
                return@hookMainActivity
            }

            appContext.actionManager.onNewIntent(this.intent)
            appContext.reloadConfig()
            syncRemote()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun init() {
        measureTime {
            with(appContext) {
                reloadConfig()
                initNative()
                executeAsync {
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
                scriptRuntime.connect(bridgeClient.getScriptingInterface())
            }
        }.also { time ->
            appContext.log.verbose("init took $time")
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun onActivityCreate() {
        measureTime {
            with(appContext) {
                features.onActivityCreate()
                actionManager.init()
                scriptRuntime.eachModule { callFunction("module.onSnapActivity", mainActivity!!) }
            }
        }.also { time ->
            appContext.log.verbose("onActivityCreate took $time")
        }
    }

    private fun initNative() {
        // don't initialize native when not logged in
        if (!appContext.database.hasArroyo()) return
        appContext.native.apply {
            if (appContext.config.experimental.nativeHooks.globalState != true) return@apply
            initOnce(appContext.androidContext.classLoader)
            nativeUnaryCallCallback = { request ->
                appContext.event.post(UnaryCallEvent(request.uri, request.buffer)) {
                    request.buffer = buffer
                    request.canceled = canceled
                }
            }
        }
    }

    private fun syncRemote() {
        appContext.executeAsync {
            bridgeClient.sync(object : SyncCallback.Stub() {
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

            event.subscribe(SnapWidgetBroadcastReceiveEvent::class) { event ->
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

                bridgeClient.passGroupsAndFriends(
                    groups.map { it.toJson() },
                    friends.map { it.toJson() }
                )
            }
        }
    }
}