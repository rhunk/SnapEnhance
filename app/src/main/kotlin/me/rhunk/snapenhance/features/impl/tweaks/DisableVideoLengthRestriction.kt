package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.getObjectField
import me.rhunk.snapenhance.util.setObjectField

class DisableVideoLengthRestriction : Feature("DisableVideoLengthRestriction", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val gridMediaItem = context.mappings.getMappedClass("GridMediaItem")
        val gridMediaItemDurationFieldName = context.mappings.getMappedValue("GridMediaItemDurationField")

        Hooker.hookConstructor(gridMediaItem, HookStage.AFTER, {
            context.config.bool(ConfigProperty.DISABLE_VIDEO_LENGTH_RESTRICTION)
        }) {param ->
            val durationMs = param.thisObject<Any>().getObjectField(gridMediaItemDurationFieldName) as Double
            if (durationMs > 60000) {
                param.thisObject<Any>().setObjectField(gridMediaItemDurationFieldName, 60000)
            }
        }
    }
}