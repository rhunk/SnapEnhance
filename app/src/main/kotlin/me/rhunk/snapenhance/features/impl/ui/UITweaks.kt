package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook

class UITweaks : Feature("UITweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    @SuppressLint("DiscouragedApi")
    override fun onActivityCreate() {
        val hiddenElements = context.config.options(ConfigProperty.HIDE_UI_ELEMENTS)
        val isImmersiveCamera = context.config.bool(ConfigProperty.IMMERSIVE_CAMERA_PREVIEW)
        val resources = context.resources

        fun findIdentifier(name: String, defType: String) = resources.getIdentifier(name, defType, Constants.SNAPCHAT_PACKAGE_NAME)

        val displayMetrics = context.resources.displayMetrics

        val capriViewfinderDefaultCornerRadius = findIdentifier("capri_viewfinder_default_corner_radius", "dimen")
        val ngsHovaNavLargerCameraButtonSize = findIdentifier("ngs_hova_nav_larger_camera_button_size", "dimen")
        val fullScreenSurfaceView = findIdentifier("full_screen_surface_view", "id")

        val callButtonsStub = findIdentifier("call_buttons_stub", "id")
        val callButton1 = findIdentifier("friend_action_button3", "id")
        val callButton2 = findIdentifier("friend_action_button4", "id")
        
        val chatNoteRecordButton = findIdentifier("chat_note_record_button", "id")
        val chatInputBarSticker = findIdentifier("chat_input_bar_sticker", "id")
        val chatInputBarCognac = findIdentifier("chat_input_bar_cognac", "id")
    
        val spotlightTabButton = findIdentifier("ngs_spotlight_icon_container", "id")
        val storiesTabButton = findIdentifier("ngs_community_icon_container", "id")
        
        Resources::class.java.methods.first { it.name == "getDimensionPixelSize"}.hook(HookStage.AFTER,
            { isImmersiveCamera }
        ) { param ->
            val id = param.arg<Int>(0)
            if (id == capriViewfinderDefaultCornerRadius || id == ngsHovaNavLargerCameraButtonSize) {
                param.setResult(0)
            }
        }

        Hooker.hook(View::class.java, "setVisibility", HookStage.BEFORE) { methodParam ->
            val viewId = (methodParam.thisObject() as View).id
            if (viewId == chatNoteRecordButton && hiddenElements["remove_voice_record_button"] == true) {
                methodParam.setArg(0, View.GONE)
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (hiddenElements["remove_call_buttons"] == false) return@hook
                methodParam.setArg(0, View.GONE)
            }
        }

        ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java
        ).hook(HookStage.BEFORE) { param ->
            val view: View = param.arg(0)
            val viewId = view.id

            if (isImmersiveCamera && view.id == fullScreenSurfaceView) {
                Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) { param ->
                    param.setArg(3, displayMetrics.heightPixels)
                }
            }

            if (viewId == chatNoteRecordButton && hiddenElements["remove_voice_record_button"] == true) {
                view.isEnabled = false
                view.setWillNotDraw(true)
            }

            if (chatInputBarCognac == viewId && hiddenElements["remove_cognac_button"] == true) {
                view.visibility = View.GONE
            }
            if (chatInputBarSticker == viewId && hiddenElements["remove_stickers_button"] == true) {
                view.visibility = View.GONE
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (hiddenElements["remove_call_buttons"] == false) return@hook
                if (view.visibility == View.GONE) return@hook
            }
            if (viewId == callButtonsStub) {
                if (hiddenElements["remove_call_buttons"] == false) return@hook
                param.setResult(null)
            }
            if(viewId == spotlightTabButton  && hiddenElements["remove_spotlight_button"] == true) {
                view.visibility = View.GONE
            }
            if(viewId == storiesTabButton && hiddenElements["remove_stories_button"] == true) {
                view.visibility = View.GONE
            }
        }
    }
}