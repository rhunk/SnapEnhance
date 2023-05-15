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
import me.rhunk.snapenhance.bridge.client.BridgeClient
import me.rhunk.snapenhance.database.DatabaseAccess
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.manager.impl.ConfigManager
import me.rhunk.snapenhance.manager.impl.FeatureManager
import me.rhunk.snapenhance.manager.impl.MappingManager
import me.rhunk.snapenhance.manager.impl.TranslationManager
import me.rhunk.snapenhance.util.download.DownloadServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    lateinit var androidContext: Context
    var mainActivity: Activity? = null

    val gson: Gson = GsonBuilder().create()

    val bridgeClient = BridgeClient(this)
    val translation = TranslationManager(this)
    val features = FeatureManager(this)
    val mappings = MappingManager(this)
    val config = ConfigManager(this)
    val database = DatabaseAccess(this)
    val downloadServer = DownloadServer(this)
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

    fun restartApp() {
        androidContext.packageManager.getLaunchIntentForPackage(
            Constants.SNAPCHAT_PACKAGE_NAME
        )?.let {
            val intent = Intent.makeRestartActivityTask(it.component)
            androidContext.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    fun softRestartApp() {
        exitProcess(0)
    }

    fun delayForceCloseApp(delay: Long) = Handler(Looper.getMainLooper()).postDelayed({
        forceCloseApp()
    }, delay)

    fun forceCloseApp() {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }
}