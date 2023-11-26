package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class DisableComposerModules : Feature("Disable Composer Modules", FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val disabledComposerModules = context.config.experimental.disableComposerModules.get().takeIf { it.isNotEmpty() }
            ?.replace(" ", "")
            ?.split(",")
            ?: return

        context.native.nativeShouldLoadAsset = callback@{ assetName ->
            if (!assetName.endsWith(".composermodule")) return@callback true
            val moduleName = assetName.replace(".composermodule", "")
            disabledComposerModules.contains(moduleName).not().also {
                if (it) context.log.debug("Loading $moduleName composer module")
                else context.log.warn("Skipping $moduleName composer module")
            }
        }
    }
}