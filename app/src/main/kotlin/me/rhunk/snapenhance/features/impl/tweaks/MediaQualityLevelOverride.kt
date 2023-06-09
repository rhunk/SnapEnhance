package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class MediaQualityLevelOverride : Feature("MediaQualityLevelOverride", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val enumQualityLevel = context.mappings.getMappedClass("enums", "QualityLevel")

        Hooker.hook(context.mappings.getMappedClass("MediaQualityLevelProvider"),
            context.mappings.getMappedValue("MediaQualityLevelProviderMethod"),
            HookStage.BEFORE,
            { context.config.bool(ConfigProperty.FORCE_MEDIA_SOURCE_QUALITY) }
        ) { param ->
            param.setResult(enumQualityLevel.enumConstants.firstOrNull { it.toString() == "LEVEL_MAX" } )
        }
    }
}