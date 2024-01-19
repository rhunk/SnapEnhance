package me.rhunk.snapenhance.core.features.impl.messaging

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.children
import me.rhunk.snapenhance.core.util.hook.HookAdapter
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getId

class CallStartConfirmation : Feature("CallStartConfirmation", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private fun hookTouchEvent(param: HookAdapter, motionEvent: MotionEvent, onConfirm: () -> Unit) {
        if (motionEvent.action != MotionEvent.ACTION_UP) return
        param.setResult(true)
        ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle(context.translation["call_start_confirmation.dialog_title"])
            .setMessage(context.translation["call_start_confirmation.dialog_message"])
            .setPositiveButton(context.translation["button.positive"]) { _, _ -> onConfirm() }
            .setNeutralButton(context.translation["button.negative"]) { _, _ -> }
            .show()
    }

    @SuppressLint("DiscouragedApi")
    override fun onActivityCreate() {
        if (!context.config.messaging.callStartConfirmation.get()) return

        val callButtonsStub = context.resources.getId("call_buttons_stub")

        findClass("com.snap.composer.views.ComposerRootView").hook("dispatchTouchEvent", HookStage.BEFORE) { param ->
            val view = param.thisObject() as? ViewGroup ?: return@hook
            if (view.id != callButtonsStub) return@hook
            val childComposerView = view.getChildAt(0) as? ViewGroup ?: return@hook
            // check if the child composer view contains 2 call buttons
            if (childComposerView.children().count {
                it::class.java == childComposerView::class.java
            } != 2) return@hook
            hookTouchEvent(param, param.arg(0)) {
                param.invokeOriginal()
            }
        }

        val callButton1 = context.resources.getId("friend_action_button3")
        val callButton2 = context.resources.getId("friend_action_button4")

        findClass("com.snap.ui.view.stackdraw.StackDrawLayout").hook("onTouchEvent", HookStage.BEFORE) { param ->
            val view = param.thisObject<View>()
            if (view.id != callButton1 && view.id != callButton2) return@hook

            hookTouchEvent(param, param.arg(0)) {
                arrayOf(
                    MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0),
                    MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
                ).forEach {
                    param.invokeOriginal(arrayOf(it))
                }
            }
        }
    }
}