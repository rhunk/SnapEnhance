package me.rhunk.snapenhance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.download.DownloadTaskManager
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.ModMappingsInfo
import me.rhunk.snapenhance.ui.manager.data.SnapchatAppInfo
import me.rhunk.snapenhance.ui.setup.Requirements
import me.rhunk.snapenhance.ui.setup.SetupActivity
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

class RemoteSideContext(
    ctx: Context
) {
    private var _context: WeakReference<Context> = WeakReference(ctx)
    private var _activity: WeakReference<Activity>? = null

    var androidContext: Context
        get() = synchronized(this) {
            _context.get() ?: error("Context is null")
        }
        set(value) { synchronized(this) {
            _context.clear(); _context = WeakReference(value)
        } }

    var activity: Activity?
        get() = _activity?.get()
        set(value) { _activity?.clear(); _activity = WeakReference(value) }

    val config = ModConfig()
    val translation = LocaleWrapper()
    val mappings = MappingsWrapper(androidContext)
    val downloadTaskManager = DownloadTaskManager()

    init {
        runCatching {
            config.loadFromContext(androidContext)
            translation.userLocale = config.locale
            translation.loadFromContext(androidContext)
            mappings.apply {
                loadFromContext(androidContext)
                init()
            }
            downloadTaskManager.init(androidContext)
        }.onFailure {
            Logger.error("Failed to initialize RemoteSideContext", it)
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

    fun checkForRequirements(overrideRequirements: Int? = null) {
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

        if (requirements == 0) return

        val currentContext = activity ?: androidContext

        Intent(currentContext, SetupActivity::class.java).apply {
            putExtra("requirements", requirements)
            if (currentContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                currentContext.startActivity(this)
                return@apply
            }
            currentContext.startActivityForResult(this, 22)
        }

        if (currentContext !is Activity) {
            exitProcess(0)
        }
    }
}