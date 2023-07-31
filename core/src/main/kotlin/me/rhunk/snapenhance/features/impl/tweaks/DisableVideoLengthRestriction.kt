package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DisableVideoLengthRestriction : Feature("DisableVideoLengthRestriction", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val defaultMediaItem = context.mappings.getMappedClass("DefaultMediaItem")
        val isState by context.config.global.disableVideoLengthRestrictions

        Hooker.hookConstructor(defaultMediaItem, HookStage.BEFORE, { isState }) { param ->
            //set the video length argument
            param.setArg(5, -1L)
        }
    }
}