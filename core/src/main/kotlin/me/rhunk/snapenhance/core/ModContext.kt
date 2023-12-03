package me.rhunk.snapenhance.core

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.common.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.common.config.ModConfig
import me.rhunk.snapenhance.core.bridge.BridgeClient
import me.rhunk.snapenhance.core.bridge.loadFromBridge
import me.rhunk.snapenhance.core.database.DatabaseAccess
import me.rhunk.snapenhance.core.event.EventBus
import me.rhunk.snapenhance.core.event.EventDispatcher
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.manager.impl.ActionManager
import me.rhunk.snapenhance.core.manager.impl.FeatureManager
import me.rhunk.snapenhance.core.messaging.CoreMessagingBridge
import me.rhunk.snapenhance.core.messaging.MessageSender
import me.rhunk.snapenhance.core.scripting.CoreScriptRuntime
import me.rhunk.snapenhance.core.util.media.HttpServer
import me.rhunk.snapenhance.nativelib.NativeConfig
import me.rhunk.snapenhance.nativelib.NativeLib
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext(
    val androidContext: Context
) {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var bridgeClient: BridgeClient
    var mainActivity: Activity? = null

    val classCache get() = SnapEnhance.classCache
    val resources: Resources get() = androidContext.resources
    val gson: Gson = GsonBuilder().create()

    private val _config = ModConfig(androidContext)
    val config by _config::root
    val log by lazy { CoreLogger(this.bridgeClient) }
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
    val scriptRuntime by lazy { CoreScriptRuntime(this, log) }
    val messagingBridge = CoreMessagingBridge(this)

    val isDeveloper by lazy { config.scripting.developerMode.get() }

    var isMainActivityPaused = false

    fun <T : Feature> feature(featureClass: KClass<T>): T {
        return features.get(featureClass)!!
    }

    fun runOnUiThread(runnable: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable()
            return
        }
        Handler(Looper.getMainLooper()).post {
            runCatching(runnable).onFailure {
                CoreLogger.xposedLog("UI thread runnable failed", it)
            }
        }
    }

    fun executeAsync(runnable: suspend ModContext.() -> Unit) {
        coroutineScope.launch {
            runCatching {
                runnable()
            }.onFailure {
                longToast("Async task failed " + it.message)
                log.error("Async task failed", it)
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
        logCritical(message, throwable ?: Throwable())
        delayForceCloseApp(100)
    }

    fun logCritical(message: Any?, throwable: Throwable = Throwable()) {
        log.error(message ?: "Snapchat crash", throwable)
        longToast(message ?: "Snapchat has crashed! Please check logs for more details.")
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
        _config.loadFromCallback { file ->
            file.loadFromBridge(bridgeClient)
        }
        native.loadNativeConfig(
            NativeConfig(
                disableBitmoji = config.experimental.nativeHooks.disableBitmoji.get(),
                disableMetrics = config.global.disableMetrics.get(),
                hookAssetOpen = config.experimental.disableComposerModules.get().isNotEmpty()
            )
        )
    }

    fun getConfigLocale(): String {
        return _config.locale
    }

    fun copyToClipboard(data: String, label: String = "Copied Text") {
        androidContext.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, data))
    }
}