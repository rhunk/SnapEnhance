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
import java.util.WeakHashMap

//TODO: bridge shared preferences
class SuspendLocationUpdates : BridgeFileFeature(
    "Suspend Location Updates",
    loadParams = FeatureLoadParams.INIT_SYNC or FeatureLoadParams.ACTIVITY_CREATE_SYNC,
    bridgeFileType = BridgeFileType.SUSPEND_LOCATION_STATE
) {
    private val streamSendHandlerInstanceMap = WeakHashMap<Any, () -> Unit>()
    private val isEnabled get() = context.config.global.suspendLocationUpdates.get()

    override fun init() {
        if (!isEnabled) return
        reload()

        findClass("com.snapchat.client.grpc.ClientStreamSendHandler\$CppProxy").hook("send", HookStage.BEFORE) { param ->
            if (param.nullableThisObject<Any>() !in streamSendHandlerInstanceMap) return@hook
            if (!exists("true")) return@hook
            param.setResult(null)
        }

        context.classCache.unifiedGrpcService.apply {
            hook("unaryCall", HookStage.BEFORE) { param ->
                val uri = param.arg<String>(0)
                if (exists("true") && uri == "/snapchat.valis.Valis/SendClientUpdate") {
                    param.setResult(null)
                }
            }

            hook("bidiStreamingCall", HookStage.AFTER) { param ->
                val uri = param.arg<String>(0)
                if (uri != "/snapchat.valis.Valis/Communicate") return@hook
                param.getResult()?.let { instance ->
                    streamSendHandlerInstanceMap[instance] = {
                        runCatching {
                            instance::class.java.methods.first { it.name == "closeStream" }.invoke(instance)
                        }.onFailure {
                            context.log.error("Failed to close stream send handler instance", it)
                        }
                    }
                }
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
                        if (isChecked) {
                            streamSendHandlerInstanceMap.entries.removeIf { (_, closeStream) ->
                                closeStream()
                                true
                            }
                        }
                        setState("true", isChecked)
                    }
                })
            }
        }
    }
}