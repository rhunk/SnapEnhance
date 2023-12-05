package me.rhunk.snapenhance.core

import android.app.Activity
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.bridge.ConfigStateListener
import me.rhunk.snapenhance.bridge.SyncCallback
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.ReceiversConfig
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.bridge.loadFromBridge
import me.rhunk.snapenhance.core.data.SnapClassCache
import me.rhunk.snapenhance.core.event.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.util.LSPatchUpdater
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import kotlin.system.measureTimeMillis


class SnapEnhance {
    companion object {
        lateinit var classLoader: ClassLoader
            private set
        val classCache by lazy {
            SnapClassCache(classLoader)
        }
    }
    private lateinit var appContext: ModContext
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
            appContext = ModContext(
                androidContext = param.arg<Context>(0).also { classLoader = it.classLoader }
            )
            appContext.apply {
                bridgeClient = BridgeClient(this)
                bridgeClient.apply {
                    connect(
                        onFailure = {
                            crash("Snapchat can't connect to the SnapEnhance app. Please download stable version from https://github.com/rhunk/SnapEnhance/releases", it)
                        }
                    ) { bridgeResult ->
                        if (!bridgeResult) {
                            logCritical("Cannot connect to the SnapEnhance app")
                            softRestartApp()
                            return@connect
                        }
                        runCatching {
                            LSPatchUpdater.onBridgeConnected(appContext, bridgeClient)
                        }.onFailure {
                            logCritical("Failed to init LSPatchUpdater", it)
                        }
                        runCatching {
                            measureTimeMillis {
                                runBlocking {
                                    init(this)
                                }
                            }.also {
                                appContext.log.verbose("init took ${it}ms")
                            }
                        }.onSuccess {
                            isBridgeInitialized = true
                        }.onFailure {
                            logCritical("Failed to initialize bridge", it)
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
            appContext.isMainActivityPaused = true
        }

        var activityWasResumed = false
        //we need to reload the config when the app is resumed
        //FIXME: called twice at first launch
        hookMainActivity("onResume") {
            appContext.isMainActivityPaused = false
            if (!activityWasResumed) {
                activityWasResumed = true
                return@hookMainActivity
            }

            appContext.actionManager.onNewIntent(this.intent)
            appContext.reloadConfig()
            syncRemote()
        }
    }

    private fun init(scope: CoroutineScope) {
        with(appContext) {
            Thread::class.java.hook("dispatchUncaughtException", HookStage.BEFORE) { param ->
                runCatching {
                    val throwable = param.argNullable(0) ?: Throwable()
                    logCritical(null, throwable)
                }
            }

            reloadConfig()
            actionManager.init()
            initConfigListener()
            scope.launch(Dispatchers.IO) {
                initNative()
                translation.userLocale = getConfigLocale()
                translation.loadFromCallback { locale ->
                    bridgeClient.fetchLocales(locale)
                }
            }

            mappings.loadFromBridge(bridgeClient)
            mappings.init(androidContext)
            database.init()
            eventDispatcher.init()
            //if mappings aren't loaded, we can't initialize features
            if (!mappings.isMappingsLoaded()) return
            bridgeClient.registerMessagingBridge(messagingBridge)
            features.init()
            scriptRuntime.connect(bridgeClient.getScriptingInterface())
            scriptRuntime.eachModule { callFunction("module.onBeforeApplicationLoad", androidContext) }
            syncRemote()
        }
    }

    private fun onActivityCreate() {
        measureTimeMillis {
            with(appContext) {
                features.onActivityCreate()
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

    private fun initConfigListener() {
        val tasks = linkedSetOf<() -> Unit>()
        hookMainActivity("onResume") {
            tasks.forEach { it() }
        }

        fun runLater(task: () -> Unit) {
            if (appContext.isMainActivityPaused) {
                tasks.add(task)
            } else {
                task()
            }
        }

        appContext.apply {
            bridgeClient.registerConfigStateListener(object: ConfigStateListener.Stub() {
                override fun onConfigChanged() {
                    log.verbose("onConfigChanged")
                    reloadConfig()
                }

                override fun onRestartRequired() {
                    log.verbose("onRestartRequired")
                    runLater {
                        log.verbose("softRestart")
                        softRestartApp(saveSettings = false)
                    }
                }

                override fun onCleanCacheRequired() {
                    log.verbose("onCleanCacheRequired")
                    tasks.clear()
                    runLater {
                        log.verbose("cleanCache")
                        actionManager.execute(EnumAction.CLEAN_CACHE)
                    }
                }
            })
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
                if (event.action != ReceiversConfig.BRIDGE_SYNC_ACTION) return@subscribe
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