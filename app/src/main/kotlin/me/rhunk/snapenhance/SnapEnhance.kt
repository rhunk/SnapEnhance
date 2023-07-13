package me.rhunk.snapenhance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.getApplicationInfoCompat
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
            if (isMainActivityNotNull || !appContext.mappings.areMappingsLoaded) return@hook
            onActivityCreate()
        }

        var activityWasResumed = false

        //we need to reload the config when the app is resumed
        Activity::class.java.hook("onResume", HookStage.AFTER, { isBridgeInitialized }) {
            val activity = it.thisObject() as Activity

            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook

            if (!activityWasResumed) {
                activityWasResumed = true
                return@hook
            }

            Logger.debug("Reloading config")
            appContext.config.loadFromBridge(appContext.bridgeClient)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun init() {
        //load translations in a coroutine to speed up initialization
        withContext(appContext.coroutineDispatcher) {
            appContext.translation.loadFromBridge(appContext.bridgeClient)
        }

        measureTime {
            with(appContext) {
                config.loadFromBridge(bridgeClient)
                mappings.init()
                //if mappings aren't loaded, we can't initialize features
                if (!mappings.areMappingsLoaded) return
                features.init()
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
}