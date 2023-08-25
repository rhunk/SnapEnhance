package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.eventbus.events.impl.AddViewEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook

@SuppressLint("DiscouragedApi")
class StartupPageOverride : Feature("StartupPageOverride", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private var ngsIcon: View? = null
    /*
    navbar icons:
        ngs_map_icon_container
        ngs_chat_icon_container
        ngs_camera_icon_container
        ngs_community_icon_container
        ngs_spotlight_icon_container
        ngs_search_icon_container
     */

    private fun clickNgsIcon() {
        Handler(context.androidContext.mainLooper).postDelayed({
            ngsIcon?.callOnClick()
        }, 300)
    }

    override fun onActivityCreate() {
        val ngsIconName = context.config.userInterface.startupTab.getNullable() ?: return

        context.androidContext.classLoader.loadClass("com.snap.mushroom.MainActivity").apply {
            hook("onResume", HookStage.AFTER) { clickNgsIcon() }
        }

        val ngsIconId = context.androidContext.resources.getIdentifier(ngsIconName, "id", Constants.SNAPCHAT_PACKAGE_NAME)

        lateinit var unhook: () -> Unit

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent !is LinearLayout) return@subscribe
            with(event.view) {
                if (id == ngsIconId) {
                    ngsIcon = this
                    unhook()
                }
            }
        }.also { unhook = it }
    }
}