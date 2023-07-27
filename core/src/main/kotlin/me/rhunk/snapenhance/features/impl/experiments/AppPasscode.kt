package me.rhunk.snapenhance.features.impl.experiments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.ui.ViewAppearanceHelper

//TODO: fingerprint unlock
class AppPasscode : Feature("App Passcode", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private var isLocked = false

    private fun setActivityVisibility(isVisible: Boolean) {
        context.mainActivity?.let {
            it.window.attributes = it.window.attributes.apply { alpha = if (isVisible) 1.0F else 0.0F }
        }
    }

    fun lock() {
        if (isLocked) return
        isLocked = true
        val passcode = context.config.string(ConfigProperty.APP_PASSCODE).also { if (it.isEmpty()) return }
        val isDigitPasscode = passcode.all { it.isDigit() }

        val mainActivity = context.mainActivity!!
        setActivityVisibility(false)

        val prompt = ViewAppearanceHelper.newAlertDialogBuilder(mainActivity)
        val createPrompt  = {
            val alertDialog = prompt.create()
            val textView = EditText(mainActivity)
            textView.setSingleLine()
            textView.inputType = if (isDigitPasscode) {
                (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            } else {
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            }
            textView.hint = "Code :"
            textView.setPadding(100, 100, 100, 100)

            textView.addTextChangedListener(object: TextWatcher {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.contentEquals(passcode)) {
                        alertDialog.dismiss()
                        isLocked = false
                        setActivityVisibility(true)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
            })

            alertDialog.setView(textView)

            textView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                if (!hasFocus) return@addOnWindowFocusChangeListener
                val imm = mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT)
            }

            alertDialog.window?.let {
                it.attributes.verticalMargin = -0.18F
            }

            alertDialog.show()
            textView.requestFocus()
        }

        prompt.setOnCancelListener {
            createPrompt()
        }

        createPrompt()
    }

    @SuppressLint("MissingPermission")
    override fun onActivityCreate() {
        if (!context.database.hasArroyo()) return

        context.runOnUiThread {
            lock()
        }

        if (!context.config.bool(ConfigProperty.APP_LOCK_ON_RESUME)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.mainActivity?.registerActivityLifecycleCallbacks(object: android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity: android.app.Activity) { lock() }
                override fun onActivityResumed(activity: android.app.Activity) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            })
        }
    }
}