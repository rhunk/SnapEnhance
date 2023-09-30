package me.rhunk.snapenhance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.rhunk.snapenhance.core.Logger
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.core.database.DatabaseAccess
import me.rhunk.snapenhance.core.event.EventBus
import me.rhunk.snapenhance.core.event.EventDispatcher
import me.rhunk.snapenhance.core.util.download.HttpServer
import me.rhunk.snapenhance.data.MessageSender
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.manager.impl.ActionManager
import me.rhunk.snapenhance.manager.impl.FeatureManager
import me.rhunk.snapenhance.nativelib.NativeConfig
import me.rhunk.snapenhance.nativelib.NativeLib
import me.rhunk.snapenhance.scripting.core.CoreScriptRuntime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    lateinit var androidContext: Context
    lateinit var bridgeClient: BridgeClient
    var mainActivity: Activity? = null

    val classCache get() = SnapEnhance.classCache
    val resources: Resources get() = androidContext.resources
    val gson: Gson = GsonBuilder().create()

    private val _config = ModConfig()
    val config by _config::root
    val log by lazy { Logger(this.bridgeClient) }
    val translation = LocaleWrapper()
    val httpServer = HttpServer()
    val messageSender = MessageSender(this)

    val features = FeatureManager(this)
    val mappings = MappingsWrapper()
    val actionManager = ActionManager(this)
    val database = DatabaseAccess(this)
    val event = EventBus(this)
    val eventDispatcher = EventDispatcher(this)
    val native = NativeLib()
    val scriptRuntime by lazy { CoreScriptRuntime(log, androidContext.classLoader) }

    val isDeveloper by lazy { config.scripting.developerMode.get() }

    fun <T : Feature> feature(featureClass: KClass<T>): T {
        return features.get(featureClass)!!
    }

    fun runOnUiThread(runnable: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            runCatching(runnable).onFailure {
                Logger.xposedLog("UI thread runnable failed", it)
            }
        }
    }

    fun executeAsync(runnable: ModContext.() -> Unit) {
        executorService.submit {
            runCatching {
                runnable()
            }.onFailure {
                longToast("Async task failed " + it.message)
                Logger.xposedLog("Async task failed", it)
            }
        }
    }

    fun shortToast(message: Any?) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun longToast(message: Any?) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
    }

    fun softRestartApp(saveSettings: Boolean = false) {
        if (saveSettings) {
            _config.writeConfig()
        }
        val intent: Intent? = androidContext.packageManager.getLaunchIntentForPackage(
            Constants.SNAPCHAT_PACKAGE_NAME
        )
        intent?.let {
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            androidContext.startActivity(mainIntent)
        }
        exitProcess(1)
    }

    fun crash(message: String, throwable: Throwable? = null) {
        Logger.xposedLog(message, throwable ?: Exception())
        longToast(message)
        delayForceCloseApp(100)
    }

    private fun delayForceCloseApp(delay: Long) = Handler(Looper.getMainLooper()).postDelayed({
        forceCloseApp()
    }, delay)

    fun forceCloseApp() {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    fun reloadConfig() {
        log.verbose("reloading config")
        _config.loadFromBridge(bridgeClient)
        native.loadNativeConfig(
            NativeConfig(
                disableBitmoji = config.experimental.nativeHooks.disableBitmoji.get(),
                disableMetrics = config.global.disableMetrics.get()
            )
        )
    }

    fun getConfigLocale(): String {
        return _config.locale
    }
}