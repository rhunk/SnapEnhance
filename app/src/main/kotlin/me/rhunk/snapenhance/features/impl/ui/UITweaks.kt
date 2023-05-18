package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class UITweaks : Feature("UITweaks", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    @SuppressLint("DiscouragedApi")
    override fun onActivityCreate() {
        val resources = context.resources

        val callButtonsStub = resources.getIdentifier("call_buttons_stub", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val callButton1 = resources.getIdentifier("friend_action_button3", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val callButton2 = resources.getIdentifier("friend_action_button4", "id", Constants.SNAPCHAT_PACKAGE_NAME)

        val chatNoteRecordButton = resources.getIdentifier("chat_note_record_button", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val chatInputBarSticker = resources.getIdentifier("chat_input_bar_sticker", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val chatInputBarCognac = resources.getIdentifier("chat_input_bar_cognac", "id", Constants.SNAPCHAT_PACKAGE_NAME)

        Hooker.hook(View::class.java, "setVisibility", HookStage.BEFORE) { methodParam ->
            val viewId = (methodParam.thisObject() as View).id
            if (viewId == chatNoteRecordButton && context.config.bool(ConfigProperty.REMOVE_VOICE_RECORD_BUTTON)) {
                methodParam.setArg(0, View.GONE)
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (!context.config.bool(ConfigProperty.REMOVE_CALL_BUTTONS)) return@hook
                methodParam.setArg(0, View.GONE)
            }
        }

        //TODO: use the event bus to dispatch a addView event
        val addViewMethod = ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java
        )
        Hooker.hook(addViewMethod, HookStage.BEFORE) { param ->
            val view: View = param.arg(0)
            val viewId = view.id

            if (chatInputBarCognac == viewId && context.config.bool(ConfigProperty.REMOVE_COGNAC_BUTTON)) {
                view.visibility = View.GONE
            }
            if (chatInputBarSticker == viewId && context.config.bool(ConfigProperty.REMOVE_STICKERS_BUTTON)) {
                view.visibility = View.GONE
            }
            if (viewId == callButton1 || viewId == callButton2) {
                if (!context.config.bool(ConfigProperty.REMOVE_CALL_BUTTONS)) return@hook
                if (view.visibility == View.GONE) return@hook
            }
            if (viewId == callButtonsStub) {
                if (!context.config.bool(ConfigProperty.REMOVE_CALL_BUTTONS)) return@hook
                param.setResult(null)
            }
        }
    }
}