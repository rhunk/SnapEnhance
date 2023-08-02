package me.rhunk.snapenhance.ui.manager

import android.content.Context
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.core.config.ModConfig
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.ModMappingsInfo
import me.rhunk.snapenhance.ui.manager.data.SnapchatAppInfo

class ManagerContext(
    private val context: Context
) {
    val config = ModConfig()
    val translation = LocaleWrapper()
    val mappings = MappingsWrapper(context)

    init {
        config.loadFromContext(context)
        translation.loadFromContext(context)
        mappings.apply { loadFromContext(context) }.init()
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
}