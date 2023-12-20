package me.rhunk.snapenhance.core.features.impl.global

import android.view.ViewGroup
import android.widget.Switch
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.BridgeFileFeature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getId

//TODO: bridge shared preferences
class SuspendLocationUpdates : BridgeFileFeature(
    "Suspend Location Updates",
    loadParams = FeatureLoadParams.INIT_SYNC or FeatureLoadParams.ACTIVITY_CREATE_SYNC,
    bridgeFileType = BridgeFileType.SUSPEND_LOCATION_STATE
) {
    private val isEnabled get() = context.config.global.suspendLocationUpdates.get()
    override fun init() {
        if (!isEnabled) return
        reload()

        context.classCache.unifiedGrpcService.hook("bidiStreamingCall", HookStage.BEFORE) { param ->
            val uri = param.arg<String>(0)
            if (uri == "/snapchat.valis.Valis/Communicate" && exists("true")) {
                param.setResult(null)
            }
        }
    }

    override fun onActivityCreate() {
        if (!isEnabled) return

        val locationSharingSettingsContainerId = context.resources.getId("location_sharing_settings_container")
        val recyclerViewContainerId = context.resources.getId("recycler_view_container")

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent.id == locationSharingSettingsContainerId && event.view.id == recyclerViewContainerId) {
                (event.view as ViewGroup).addView(Switch(event.view.context).apply {
                    isChecked = exists("true")
                    ViewAppearanceHelper.applyTheme(this)
                    text = this@SuspendLocationUpdates.context.translation["suspend_location_updates.switch_text"]
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setOnCheckedChangeListener { _, isChecked ->
                        setState("true", isChecked)
                    }
                })
            }
        }
    }
}