package me.rhunk.snapenhance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.CoreComponentFactory
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.rhunk.snapenhance.bridge.BridgeService
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.core.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.download.DownloadTaskManager
import me.rhunk.snapenhance.e2ee.E2EEImplementation
import me.rhunk.snapenhance.messaging.ModDatabase
import me.rhunk.snapenhance.messaging.StreaksReminder
import me.rhunk.snapenhance.scripting.RemoteScriptManager
import me.rhunk.snapenhance.ui.overlay.SettingsOverlay
import me.rhunk.snapenhance.ui.manager.MainActivity
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.ModInfo
import me.rhunk.snapenhance.ui.manager.data.PlatformInfo
import me.rhunk.snapenhance.ui.manager.data.SnapchatAppInfo
import me.rhunk.snapenhance.ui.setup.Requirements
import me.rhunk.snapenhance.ui.setup.SetupActivity
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


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
    val log = LogManager(this)
    val scriptManager = RemoteScriptManager(this)
    val settingsOverlay = SettingsOverlay(this)
    val e2eeImplementation = E2EEImplementation(this)

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
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun reload() {
        log.verbose("Loading RemoteSideContext")
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
            scriptManager.init()
        }.onFailure {
            log.error("Failed to load RemoteSideContext", it)
        }

        scriptManager.runtime.eachModule {
            callFunction("module.onManagerLoad",androidContext)
        }
    }

    val installationSummary by lazy {
        InstallationSummary(
            snapchatInfo = mappings.getSnapchatPackageInfo()?.let {
                SnapchatAppInfo(
                    packageName = it.packageName,
                    version = it.versionName,
                    versionCode = it.longVersionCode,
                    isLSPatched = it.applicationInfo.appComponentFactory != CoreComponentFactory::class.java.name,
                    isSplitApk = it.splitNames?.isNotEmpty() ?: false
                )
            },
            modInfo = ModInfo(
                loaderPackageName = MainActivity::class.java.`package`?.name,
                buildPackageName = BuildConfig.APPLICATION_ID,
                buildVersion = BuildConfig.VERSION_NAME,
                buildVersionCode = BuildConfig.VERSION_CODE.toLong(),
                buildIssuer = androidContext.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?.signingInfo?.apkContentsSigners?.firstOrNull()?.let {
                        val certFactory = CertificateFactory.getInstance("X509")
                        val cert = certFactory.generateCertificate(ByteArrayInputStream(it.toByteArray())) as X509Certificate
                        cert.issuerDN.toString()
                    } ?: throw Exception("Failed to get certificate info"),
                isDebugBuild = BuildConfig.DEBUG,
                mappingVersion = mappings.getGeneratedBuildNumber(),
                mappingsOutdated = mappings.isMappingsOutdated()
            ),
            platformInfo = PlatformInfo(
                device = Build.DEVICE,
                androidVersion = Build.VERSION.RELEASE,
                systemAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            )
        )
    }

    fun longToast(message: Any) {
        androidContext.mainExecutor.execute {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
        log.debug(message.toString())
    }

    fun shortToast(message: Any) {
        androidContext.mainExecutor.execute {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
        log.debug(message.toString())
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