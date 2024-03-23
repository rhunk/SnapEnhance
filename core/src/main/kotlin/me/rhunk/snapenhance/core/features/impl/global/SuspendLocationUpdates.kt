package me.rhunk.snapenhance.core.features.impl.global

import android.view.ViewGroup
import android.widget.Switch
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.BridgeFileFeature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.util.ktx.getId

class SuspendLocationUpdates : BridgeFileFeature(
    "Suspend Location Updates",
    loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC, bridgeFileType = BridgeFileType.SUSPEND_LOCATION_STATE) {
    fun isSuspended() = exists("true")
    private fun setSuspended(suspended: Boolean) = setState("true", suspended)

    override fun onActivityCreate() {
        if (context.config.global.betterLocation.takeIf { it.globalState == true }?.suspendLocationUpdates?.get() != true) return
        reload()

        val locationSharingSettingsContainerId = context.resources.getId("location_sharing_settings_container")
        val recyclerViewContainerId = context.resources.getId("recycler_view_container")

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent.id == locationSharingSettingsContainerId && event.view.id == recyclerViewContainerId) {
                (event.view as ViewGroup).addView(Switch(event.view.context).apply {
                    isChecked = isSuspended()
                    ViewAppearanceHelper.applyTheme(this)
                    text = this@SuspendLocationUpdates.context.translation["suspend_location_updates.switch_text"]
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setOnCheckedChangeListener { _, isChecked ->
                        setSuspended(isChecked)
                    }
                })
            }
        }
    }
}