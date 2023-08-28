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
import kotlinx.coroutines.asCoroutineDispatcher
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.core.eventbus.EventBus
import me.rhunk.snapenhance.data.MessageSender
import me.rhunk.snapenhance.database.DatabaseAccess
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.manager.impl.ActionManager
import me.rhunk.snapenhance.manager.impl.FeatureManager
import me.rhunk.snapenhance.nativelib.NativeConfig
import me.rhunk.snapenhance.nativelib.NativeLib
import me.rhunk.snapenhance.util.download.HttpServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    val coroutineDispatcher by lazy {
        executorService.asCoroutineDispatcher()
    }

    lateinit var androidContext: Context
    var mainActivity: Activity? = null
    lateinit var bridgeClient: BridgeClient

    val gson: Gson = GsonBuilder().create()

    private val modConfig = ModConfig()
    val config by modConfig
    val event = EventBus(this)
    val eventDispatcher = EventDispatcher(this)
    val native = NativeLib()

    val translation = LocaleWrapper()
    val features = FeatureManager(this)
    val mappings = MappingsWrapper()
    val actionManager = ActionManager(this)
    val database = DatabaseAccess(this)
    val httpServer = HttpServer()
    val messageSender = MessageSender(this)
    val classCache get() = SnapEnhance.classCache
    val resources: Resources get() = androidContext.resources

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

    fun executeAsync(runnable: () -> Unit) {
        executorService.submit {
            runCatching {
                runnable()
            }.onFailure {
                longToast("Async task failed " + it.message)
                Logger.xposedLog("Async task failed", it)
            }
        }
    }

    fun shortToast(message: Any) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun longToast(message: Any) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
    }

    fun softRestartApp(saveSettings: Boolean = false) {
        if (saveSettings) {
            modConfig.writeConfig()
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
        Logger.xposedLog(message, throwable)
        longToast(message)
        delayForceCloseApp(100)
    }

    fun delayForceCloseApp(delay: Long) = Handler(Looper.getMainLooper()).postDelayed({
        forceCloseApp()
    }, delay)

    fun forceCloseApp() {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    fun reloadConfig() {
        modConfig.loadFromBridge(bridgeClient)
        native.loadNativeConfig(
            NativeConfig(
                disableBitmoji = config.experimental.nativeHooks.disableBitmoji.get(),
                disableMetrics = config.global.disableMetrics.get()
            )
        )
    }

    fun getConfigLocale(): String {
        return modConfig.locale
    }
}