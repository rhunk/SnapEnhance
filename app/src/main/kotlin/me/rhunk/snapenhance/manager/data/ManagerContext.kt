package me.rhunk.snapenhance.manager.data

import android.content.Context
import me.rhunk.snapenhance.bridge.wrapper.ConfigWrapper
import me.rhunk.snapenhance.bridge.wrapper.MappingsWrapper
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper

class ManagerContext(
    private val context: Context
) {
    private val config = ConfigWrapper()
    private val translation = TranslationWrapper()
    private val mappings = MappingsWrapper(context)

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