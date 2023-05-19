package me.rhunk.snapenhance

import android.app.Activity
import android.app.Application
import android.content.Context
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

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

            appContext.bridgeClient.start { bridgeResult ->
                if (!bridgeResult) {
                    Logger.xposedLog("Cannot connect to bridge service")
                    appContext.restartApp()
                    return@start
                }
                runCatching {
                    init()
                }.onFailure {
                    Logger.xposedLog("Failed to initialize", it)
                }
            }
        }

        Hooker.hook(Activity::class.java, "onCreate", HookStage.AFTER) {
            val activity = it.thisObject() as Activity
            if (!activity.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return@hook
            val isMainActivityNotNull = appContext.mainActivity != null
            appContext.mainActivity = activity
            if (isMainActivityNotNull) return@hook
            onActivityCreate()
        }
    }

    private fun init() {
        val time = System.currentTimeMillis()
        with(appContext) {
            translation.init()
            config.init()
            mappings.init()
            features.init()
        }
        Logger.debug("initialized in ${System.currentTimeMillis() - time} ms")
    }

    private fun onActivityCreate() {
        with(appContext) {
            features.onActivityCreate()
            actionManager.init()
        }
    }
}