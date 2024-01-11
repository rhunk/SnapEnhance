package me.rhunk.snapenhance.core.features.impl.global

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.mapper.impl.MediaQualityLevelProviderMapper
import java.lang.reflect.Method

class MediaQualityLevelOverride : Feature("MediaQualityLevelOverride", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        if (!context.config.global.forceUploadSourceQuality.get()) return

        context.mappings.useMapper(MediaQualityLevelProviderMapper::class) {
            mediaQualityLevelProvider.getAsClass()?.hook(
                mediaQualityLevelProviderMethod.getAsString()!!,
                HookStage.BEFORE
            ) { param ->
                param.setResult((param.method() as Method).returnType.enumConstants.firstOrNull { it.toString() == "LEVEL_MAX" } )
            }
        }
    }
}