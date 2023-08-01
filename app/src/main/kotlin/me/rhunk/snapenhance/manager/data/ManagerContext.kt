package me.rhunk.snapenhance.manager.data

import android.content.Context
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper
import me.rhunk.snapenhance.core.config.ModConfig

class ManagerContext(
    private val context: Context
) {
    val config = ModConfig()
    val translation = TranslationWrapper()
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