package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
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

    override fun onActivityCreate() {
        val ngsIconName = context.config.state(ConfigProperty.STARTUP_PAGE_OVERRIDE).also {
            if (it == "OFF") return
        }

        context.androidContext.classLoader.loadClass("com.snap.mushroom.MainActivity").apply {
            hook("onResume", HookStage.AFTER) {
                Handler(context.androidContext.mainLooper).postDelayed({
                    ngsIcon?.callOnClick()
                }, 300)
            }
        }

        val ngsIconId = context.androidContext.resources.getIdentifier(ngsIconName, "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val unhooks = mutableListOf<() -> Unit>()

        ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java
        ).hook(HookStage.AFTER) { param ->
            if (param.thisObject<ViewGroup>() !is LinearLayout) return@hook
            with(param.arg<View>(0)) {
                if (id == ngsIconId) {
                    ngsIcon = this
                    unhooks.forEach { it() }
                }
            }
        }.also { unhooks.add(it::unhook) }
    }
}