package me.rhunk.snapenhance.features.impl.ui

import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import java.io.File


class ClientBootstrapOverride : Feature("ClientBootstrapOverride", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    companion object {
        val tabs = arrayOf("map", "chat", "camera", "discover", "spotlight")
    }

    private val clientBootstrapFolder by lazy { File(context.androidContext.filesDir, "client-bootstrap") }

    private val appearanceStartupConfigFile by lazy { File(clientBootstrapFolder, "appearancestartupconfig") }
    private val plusFile by lazy { File(clientBootstrapFolder, "plus") }

    override fun onActivityCreate() {
        val bootstrapOverrideConfig = context.config.userInterface.bootstrapOverride

        bootstrapOverrideConfig.appAppearance.getNullable()?.also { appearance ->
            val state = when (appearance) {
                "always_light" -> 0
                "always_dark" -> 1
                else -> return@also
            }.toByte()
            appearanceStartupConfigFile.writeBytes(byteArrayOf(0, 0, 0, state))
        }

        bootstrapOverrideConfig.homeTab.getNullable()?.also { currentTab ->
            plusFile.writeBytes(byteArrayOf(8, (tabs.indexOf(currentTab) + 1).toByte()))
        }
    }
}