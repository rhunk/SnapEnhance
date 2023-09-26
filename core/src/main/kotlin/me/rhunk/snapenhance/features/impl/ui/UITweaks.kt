package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
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
        val marginLayoutParams = parent.layoutParams as MarginLayoutParams
        marginLayoutParams.setMargins(-99999, -99999, -99999, -99999)
        event.canceled = true
    }

    private var surfaceViewAspectRatio: Float = 0f

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    override fun onActivityCreate() {
        val blockAds by context.config.global.blockAds
        val hiddenElements by context.config.userInterface.hideUiComponents
        val hideStorySections by context.config.userInterface.hideStorySections
        val isImmersiveCamera by context.config.camera.immersiveCameraPreview

        val displayMetrics = context.resources.displayMetrics
        val deviceAspectRatio = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()
        val statusBarHeight = run {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId)
            else Rect().apply { context.mainActivity!!.window.decorView.getWindowVisibleDisplayFrame(this) }.top
        }

        val callButtonsStub = getIdentifier("call_buttons_stub", "id")
        val callButton1 = getIdentifier("friend_action_button3", "id")
        val callButton2 = getIdentifier("friend_action_button4", "id")

        val chatNoteRecordButton = getIdentifier("chat_note_record_button", "id")

        View::class.java.hook("setVisibility", HookStage.BEFORE) { methodParam ->
            val viewId = (methodParam.thisObject() as View).id
            if (viewId == callButton1 || viewId == callButton2) {
                if (!hiddenElements.contains("hide_profile_call_buttons")) return@hook
                methodParam.setArg(0, View.GONE)
            }
        }

        Resources::class.java.methods.first { it.name == "getDimensionPixelSize"}.hook(HookStage.AFTER,
            { isImmersiveCamera }
        ) { param ->
            val id = param.arg<Int>(0)
            if (id == getIdentifier("capri_viewfinder_default_corner_radius", "dimen") ||
                id == getIdentifier("ngs_hova_nav_larger_camera_button_size", "dimen")) {
                param.setResult(0)
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

            if (hideStorySections.contains("hide_suggested") && (viewId == getIdentifier("df_small_story", "id"))
            ) {
                hideStorySection(event)
            }

            if (blockAds && viewId == getIdentifier("df_promoted_story", "id")) {
                hideStorySection(event)
            }

            if (isImmersiveCamera) {
                if (view.id == getIdentifier("full_screen_surface_view", "id")) {
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        val width = it.arg(2) as Int
                        val height = it.arg(3) as Int
                        if (height <= 0 || width <= 0) return@hookObjectMethod
                        surfaceViewAspectRatio = width.toFloat() / height.toFloat()
                        it.setArg(2, (displayMetrics.heightPixels * surfaceViewAspectRatio).toInt())
                        it.setArg(3, displayMetrics.heightPixels)
                    }
                }

                if (view.id == getIdentifier("edits_container", "id")) {
                    Hooker.hookObjectMethod(View::class.java, view, "layout", HookStage.BEFORE) {
                        //TODO: fix edits position on the canvas
                    }
                }
            }

            if (
                (viewId == chatNoteRecordButton && hiddenElements.contains("hide_voice_record_button")) ||
                (viewId == getIdentifier("chat_input_bar_sticker", "id") && hiddenElements.contains("hide_stickers_button")) ||
                (viewId == getIdentifier("chat_input_bar_sharing_drawer_button", "id") && hiddenElements.contains("hide_live_location_share_button")) ||
                (viewId == callButtonsStub && hiddenElements.contains("hide_chat_call_buttons"))
            ) {
                view.apply {
                    view.post {
                        isEnabled = false
                        setWillNotDraw(true)
                        view.visibility = View.GONE
                    }
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        view.post { view.visibility = View.GONE }
                    }
                }
            }
        }
    }
}