package me.rhunk.snapenhance.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.bridge.ConfigStateListener
import me.rhunk.snapenhance.bridge.SyncCallback
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.ReceiversConfig
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.data.FriendStreaks
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.util.toSerialized
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.bridge.loadFromBridge
import me.rhunk.snapenhance.core.data.SnapClassCache
import me.rhunk.snapenhance.core.event.events.impl.NativeUnaryCallEvent
import me.rhunk.snapenhance.core.event.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.core.util.LSPatchUpdater
import me.rhunk.snapenhance.core.util.hook.HookAdapter
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import java.lang.reflect.Modifier
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

    private fun hookMainActivity(methodName: String, stage: HookStage = HookStage.AFTER, block: Activity.(param: HookAdapter) -> Unit) {
        Activity::class.java.hook(methodName, stage, { isBridgeInitialized }) { param ->
            val activity = param.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            block(activity, param)
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
            if (isMainActivityNotNull || !appContext.mappings.isMappingsLoaded) return@hookMainActivity
            onActivityCreate()
            jetpackComposeResourceHook()
            appContext.actionManager.onNewIntent(intent)
        }

        hookMainActivity("onPause") {
            appContext.bridgeClient.closeSettingsOverlay()
            appContext.isMainActivityPaused = true
        }

        hookMainActivity("onNewIntent") { param ->
            appContext.actionManager.onNewIntent(param.argNullable(0))
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
            initNative()
            scope.launch(Dispatchers.IO) {
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
            if (!mappings.isMappingsLoaded) return
            bridgeClient.registerMessagingBridge(messagingBridge)
            features.init()
            scriptRuntime.connect(bridgeClient.getScriptingInterface())
            scriptRuntime.eachModule { callFunction("module.onSnapApplicationLoad", androidContext) }
        }
    }

    private fun onActivityCreate() {
        measureTimeMillis {
            with(appContext) {
                features.onActivityCreate()
                scriptRuntime.eachModule { callFunction("module.onSnapMainActivityCreate", mainActivity!!) }
            }
        }.also { time ->
            appContext.log.verbose("onActivityCreate took $time")
        }
    }

    private fun initNative() {
        // don't initialize native when not logged in
        if (!appContext.database.hasArroyo()) return
        if (appContext.config.experimental.nativeHooks.globalState != true) return

        lateinit var unhook: () -> Unit
        Runtime::class.java.declaredMethods.first {
            it.name == "loadLibrary0" && it.parameterTypes.contentEquals(arrayOf(ClassLoader::class.java, Class::class.java, String::class.java))
        }.hook(HookStage.AFTER) { param ->
            val libName = param.arg<String>(2)
            if (libName != "client") return@hook
            unhook()
            appContext.native.initOnce(appContext.androidContext.classLoader)
            appContext.native.nativeUnaryCallCallback = { request ->
                appContext.event.post(NativeUnaryCallEvent(request.uri, request.buffer)) {
                    request.buffer = buffer
                    request.canceled = canceled
                }
            }
        }.also { unhook = { it.unhook() } }
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

        appContext.executeAsync {
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
                    return database.getFriendInfo(uuid)?.let {
                        MessagingFriendInfo(
                            userId = it.userId!!,
                            displayName = it.displayName,
                            mutableUsername = it.mutableUsername!!,
                            bitmojiId = it.bitmojiAvatarId,
                            selfieId = it.bitmojiSelfieId,
                            streaks = if (it.streakLength > 0) {
                                FriendStreaks(
                                    expirationTimestamp = it.streakExpirationTimestamp,
                                    length = it.streakLength
                                )
                            } else null
                        ).toSerialized()
                    }
                }

                override fun syncGroup(uuid: String): String? {
                    return database.getFeedEntryByConversationId(uuid)?.let {
                        MessagingGroupInfo(
                            it.key!!,
                            it.feedDisplayName!!,
                            it.participantsSize
                        ).toSerialized()
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
                        it.bitmojiSelfieId,
                        streaks = null
                    )
                }

                bridgeClient.passGroupsAndFriends(groups, friends)
            }
        }
    }

    private fun jetpackComposeResourceHook() {
        val material3RString = try {
            Class.forName("androidx.compose.material3.R\$string")
        } catch (e: ClassNotFoundException) {
            return
        }

        val stringResources = material3RString.fields.filter {
            Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType
        }.associate { it.getInt(null) to it.name }

        Resources::class.java.getMethod("getString", Int::class.javaPrimitiveType).hook(HookStage.BEFORE) { param ->
            val key = param.arg<Int>(0)
            val name = stringResources[key] ?: return@hook
            // FIXME: prevent blank string in translations
            if (name == "date_range_input_title") {
                param.setResult("")
                return@hook
            }
            param.setResult(appContext.translation.getOrNull("material3_strings.$name") ?: return@hook)
        }
    }
}