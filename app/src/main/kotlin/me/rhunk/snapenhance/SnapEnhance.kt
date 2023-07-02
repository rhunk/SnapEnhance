package me.rhunk.snapenhance

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.bridge.AbstractBridgeClient
import me.rhunk.snapenhance.bridge.client.ServiceBridgeClient
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
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

    init {
        Hooker.hook(Application::class.java, "attach", HookStage.BEFORE) { param ->
            appContext.androidContext = param.arg<Context>(0).also {
                classLoader = it.classLoader
            }
            appContext.bridgeClient = provideBridgeClient()

            appContext.bridgeClient.apply {
                this.context = appContext
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
                    }.onFailure {
                        Logger.xposedLog("Failed to initialize", it)
                    }
                }
            }
        }

        Hooker.hook(Activity::class.java, "onCreate", HookStage.AFTER) {
            val activity = it.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            val isMainActivityNotNull = appContext.mainActivity != null
            appContext.mainActivity = activity
            if (isMainActivityNotNull || !appContext.mappings.areMappingsLoaded) return@hook
            onActivityCreate()
        }

        var activityWasResumed = false

        //we need to reload the config when the app is resumed
        Hooker.hook(Activity::class.java, "onResume", HookStage.AFTER) {
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

    @SuppressLint("ObsoleteSdkInt")
    private fun provideBridgeClient(): AbstractBridgeClient {
        /*if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return RootBridgeClient()
        }*/
        return ServiceBridgeClient()
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
            Logger.debug("initialized in $time")
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
            Logger.debug("onActivityCreate in $time")
        }
    }
}