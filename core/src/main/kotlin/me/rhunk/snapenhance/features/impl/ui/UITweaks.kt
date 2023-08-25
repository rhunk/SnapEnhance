package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.eventbus.events.impl.AddViewEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
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

    private fun hideStorySection(event: AddViewEvent) {
        val parent = event.parent
        parent.visibility = View.GONE
        val marginLayoutParams = parent.layoutParams as ViewGroup.MarginLayoutParams
        marginLayoutParams.setMargins(-99999, -99999, -99999, -99999)
        event.canceled = true
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    override fun onActivityCreate() {
        val blockAds by context.config.global.blockAds
        val hiddenElements by context.config.userInterface.hideUiComponents
        val hideStorySections by context.config.userInterface.hideStorySections
        val isImmersiveCamera by context.config.camera.immersiveCameraPreview

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
            if (viewId == chatNoteRecordButton && hiddenElements.contains("hide_voice_record_button")) {
                methodParam.setArg(0, View.GONE)
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (!hiddenElements.contains("hide_call_buttons")) return@hook
                methodParam.setArg(0, View.GONE)
            }
        }


        context.event.subscribe(AddViewEvent::class) { event ->
            val viewId = event.view.id
            val view = event.view

            if (hideStorySections.contains("hide_for_you")) {
                if (viewId == getIdentifier("df_large_story", "id") ||
                            viewId == getIdentifier("df_promoted_story", "id")) {
                    hideStorySection(event)
                    return@subscribe
                }
                if (viewId == getIdentifier("stories_load_progress_layout", "id")) {
                    event.canceled = true
                }
            }

            if (hideStorySections.contains("hide_friends") && viewId == getIdentifier("friend_card_frame", "id")) {
                hideStorySection(event)
            }

            //mappings?
            if (hideStorySections.contains("hide_friend_suggestions") && view.javaClass.superclass?.name?.endsWith("StackDrawLayout") == true) {
                val layoutParams = view.layoutParams as? FrameLayout.LayoutParams ?: return@subscribe
                if (layoutParams.width == -1 &&
                    layoutParams.height == -2 &&
                    view.javaClass.let { clazz ->
                        clazz.methods.any { it.returnType == SpannableString::class.java} &&
                        clazz.constructors.any { it.parameterCount == 1 && it.parameterTypes[0] == Context::class.java }
                    }
                ) {
                    hideStorySection(event)
                }
            }

            if (hideStorySections.contains("hide_following") && (viewId == getIdentifier("df_small_story", "id"))
            ) {
                hideStorySection(event)
            }

            if (blockAds && viewId == getIdentifier("df_promoted_story", "id")) {
                hideStorySection(event)
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

            if (viewId == chatNoteRecordButton && hiddenElements.contains("hide_voice_record_button")) {
                view.isEnabled = false
                view.setWillNotDraw(true)
            }

            if (getIdentifier("chat_input_bar_cognac", "id") == viewId && hiddenElements.contains("hide_cognac_button")) {
                view.visibility = View.GONE
            }
            if (getIdentifier("chat_input_bar_sticker", "id") == viewId && hiddenElements.contains("hide_stickers_button")) {
                view.visibility = View.GONE
            }
            if (getIdentifier("chat_input_bar_sharing_drawer_button", "id") == viewId && hiddenElements.contains("hide_live_location_share_button")) {
                event.canceled = true
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (!hiddenElements.contains("hide_call_buttons")) return@subscribe
                if (view.visibility == View.GONE) return@subscribe
            }
            if (viewId == callButtonsStub) {
                if (!hiddenElements.contains("hide_call_buttons")) return@subscribe
                event.canceled = true
            }
        }
    }
}