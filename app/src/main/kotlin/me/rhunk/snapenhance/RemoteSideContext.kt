package me.rhunk.snapenhance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import me.rhunk.snapenhance.bridge.BridgeService
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.download.DownloadTaskManager
import me.rhunk.snapenhance.messaging.ModDatabase
import me.rhunk.snapenhance.messaging.StreaksReminder
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.ModMappingsInfo
import me.rhunk.snapenhance.ui.manager.data.SnapchatAppInfo
import me.rhunk.snapenhance.ui.setup.Requirements
import me.rhunk.snapenhance.ui.setup.SetupActivity
import java.lang.ref.WeakReference

class RemoteSideContext(
    val androidContext: Context
) {
    private var _activity: WeakReference<ComponentActivity>? = null
    lateinit var bridgeService: BridgeService

    var activity: ComponentActivity?
        get() = _activity?.get()
        set(value) { _activity?.clear(); _activity = WeakReference(value) }

    val config = ModConfig()
    val translation = LocaleWrapper()
    val mappings = MappingsWrapper()
    val downloadTaskManager = DownloadTaskManager()
    val modDatabase = ModDatabase(this)
    val streaksReminder = StreaksReminder(this)

    //used to load bitmoji selfies and download previews
    val imageLoader by lazy {
        ImageLoader.Builder(androidContext)
            .dispatcher(Dispatchers.IO)
            .memoryCache {
                MemoryCache.Builder(androidContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(androidContext.cacheDir.resolve("coil-disk-cache"))
                    .maxSizeBytes(1024 * 1024 * 100) // 100MB
                    .build()
            }
            .components { add(VideoFrameDecoder.Factory()) }.build()
    }

    fun reload() {
        runCatching {
            config.loadFromContext(androidContext)
            translation.apply {
                userLocale = config.locale
                loadFromContext(androidContext)
            }
            mappings.apply {
                loadFromContext(androidContext)
                init(androidContext)
            }
            downloadTaskManager.init(androidContext)
            modDatabase.init()
            streaksReminder.init()
        }.onFailure {
            Logger.error("Failed to load RemoteSideContext", it)
        }
    }

    fun getInstallationSummary() = InstallationSummary(
        snapchatInfo = mappings.getSnapchatPackageInfo()?.let {
            SnapchatAppInfo(
                version = it.versionName,
                versionCode = it.longVersionCode
            )
        },
        mappingsInfo = if (mappings.isMappingsLoaded()) {
            ModMappingsInfo(
                generatedSnapchatVersion = mappings.getGeneratedBuildNumber(),
                isOutdated = mappings.isMappingsOutdated()
            )
        } else null
    )

    fun longToast(message: Any) {
        androidContext.mainExecutor.execute {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
        Logger.debug(message.toString())
    }

    fun shortToast(message: Any) {
        androidContext.mainExecutor.execute {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
        Logger.debug(message.toString())
    }

    fun checkForRequirements(overrideRequirements: Int? = null): Boolean {
        var requirements = overrideRequirements ?: 0

        if (!config.wasPresent) {
            requirements = requirements or Requirements.FIRST_RUN
        }

        config.root.downloader.saveFolder.get().let {
            if (it.isEmpty() || run {
                    val documentFile = runCatching { DocumentFile.fromTreeUri(androidContext, Uri.parse(it)) }.getOrNull()
                    documentFile == null || !documentFile.exists() || !documentFile.canWrite()
                }) {
                requirements = requirements or Requirements.SAVE_FOLDER
            }
        }

        if (mappings.isMappingsOutdated() || !mappings.isMappingsLoaded()) {
            requirements = requirements or Requirements.MAPPINGS
        }

        if (requirements == 0) return false

        val currentContext = activity ?: androidContext

        Intent(currentContext, SetupActivity::class.java).apply {
            putExtra("requirements", requirements)
            if (currentContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            currentContext.startActivity(this)
            return true
        }
    }
}