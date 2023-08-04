package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook

class MediaQualityLevelOverride : Feature("MediaQualityLevelOverride", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val enumQualityLevel = context.mappings.getMappedClass("EnumQualityLevel")
        val mediaQualityLevelProvider = context.mappings.getMappedMap("MediaQualityLevelProvider")

        context.androidContext.classLoader.loadClass(mediaQualityLevelProvider["class"].toString()).hook(
            mediaQualityLevelProvider["method"].toString(),
            HookStage.BEFORE,
            { context.config.bool(ConfigProperty.FORCE_MEDIA_SOURCE_QUALITY) }
        ) { param ->
            param.setResult(enumQualityLevel.enumConstants.firstOrNull { it.toString() == "LEVEL_MAX" } )
        }
    }
}