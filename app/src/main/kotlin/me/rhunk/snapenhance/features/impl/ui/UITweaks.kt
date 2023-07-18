package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook

class UITweaks : Feature("UITweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val identifierCache = mutableMapOf<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun getIdentifier(name: String, defType: String): Int {
        return identifierCache.getOrPut("$name:$defType") {
            context.resources.getIdentifier(name, defType, Constants.SNAPCHAT_PACKAGE_NAME)
        }
    }

    private fun hideStorySection(param: HookAdapter) {
        val parent = param.thisObject() as ViewGroup
        parent.visibility = View.GONE
        val marginLayoutParams = parent.layoutParams as ViewGroup.MarginLayoutParams
        marginLayoutParams.setMargins(-99999, -99999, -99999, -99999)
        param.setResult(null)
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    override fun onActivityCreate() {
        val blockAds = context.config.bool(ConfigProperty.BLOCK_ADS)
        val hiddenElements = context.config.options(ConfigProperty.HIDE_UI_ELEMENTS)
        val hideStorySection = context.config.options(ConfigProperty.HIDE_STORY_SECTION)
        val isImmersiveCamera = context.config.bool(ConfigProperty.IMMERSIVE_CAMERA_PREVIEW)

        val displayMetrics = context.resources.displayMetrics

        val callButtonsStub = getIdentifier("call_buttons_stub", "id")
        val callButton1 = getIdentifier("friend_action_button3", "id")
        val callButton2 = getIdentifier("friend_action_button4", "id")

        val chatNoteRecordButton = getIdentifier("chat_note_record_button", "id")

        Resources::class.java.methods.first { it.name == "getDimensionPixelSize"}.hook(HookStage.AFTER,
            { isImmersiveCamera }
        ) { param ->
            val id = param.arg<Int>(0)
            if (id == getIdentifier("capri_viewfinder_default_corner_radius", "dimen") ||
                id == getIdentifier("ngs_hova_nav_larger_camera_button_size", "dimen")) {
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

            if (hideStorySection["hide_for_you"] == true) {
                if (viewId == getIdentifier("df_large_story", "id") ||
                            viewId == getIdentifier("df_promoted_story", "id")) {
                    hideStorySection(param)
                    return@hook
                }
                if (viewId == getIdentifier("stories_load_progress_layout", "id")) {
                    param.setResult(null)
                }
            }

            if (hideStorySection["hide_friends"] == true && viewId == getIdentifier("friend_card_frame", "id")) {
                hideStorySection(param)
            }

            //mappings?
            if (hideStorySection["hide_friend_suggestions"] == true && view.javaClass.superclass?.name?.endsWith("StackDrawLayout") == true) {
                val layoutParams = view.layoutParams as? FrameLayout.LayoutParams ?: return@hook
                if (layoutParams.width == -1 &&
                    layoutParams.height == -2 &&
                    view.javaClass.let { clazz ->
                        clazz.methods.any { it.returnType == SpannableString::class.java} &&
                        clazz.constructors.any { it.parameterCount == 1 && it.parameterTypes[0] == Context::class.java }
                    }
                ) {
                    hideStorySection(param)
                }
            }

            if (hideStorySection["hide_following"] == true && (viewId == getIdentifier("df_small_story", "id"))
            ) {
                hideStorySection(param)
            }

            if (blockAds && viewId == getIdentifier("df_promoted_story", "id")) {
                hideStorySection(param)
            }

            if (isImmersiveCamera) {
                if (view.id == getIdentifier("edits_container", "id")) {
                    val deviceAspectRatio = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        val width = it.arg(2) as Int
                        val realHeight = (width / deviceAspectRatio).toInt()
                        it.setArg(3, realHeight)
                    }
                }
                if (view.id == getIdentifier("full_screen_surface_view", "id")) {
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        it.setArg(1, 1)
                        it.setArg(3, displayMetrics.heightPixels)
                    }
                }
            }

            if (viewId == chatNoteRecordButton && hiddenElements["remove_voice_record_button"] == true) {
                view.isEnabled = false
                view.setWillNotDraw(true)
            }

            if (getIdentifier("chat_input_bar_cognac", "id") == viewId && hiddenElements["remove_cognac_button"] == true) {
                view.visibility = View.GONE
            }
            if (getIdentifier("chat_input_bar_sticker", "id") == viewId && hiddenElements["remove_stickers_button"] == true) {
                view.visibility = View.GONE
            }
            if (getIdentifier("chat_input_bar_sharing_drawer_button", "id") == viewId && hiddenElements["remove_live_location_share_button"] == true) {
                param.setResult(null)
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (hiddenElements["remove_call_buttons"] == false) return@hook
                if (view.visibility == View.GONE) return@hook
            }
            if (viewId == callButtonsStub) {
                if (hiddenElements["remove_call_buttons"] == false) return@hook
                param.setResult(null)
            }
        }
    }
}