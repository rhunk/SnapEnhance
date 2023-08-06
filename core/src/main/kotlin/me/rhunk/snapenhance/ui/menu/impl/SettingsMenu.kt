package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import me.rhunk.snapenhance.ui.ViewAppearanceHelper

class SettingsMenu : AbstractMenu() {
    //TODO: quick settings
    @SuppressLint("SetTextI18n")
    fun inject(viewModel: View, addView: (View) -> Unit) {
        val actions = context.actionManager.getActions().map {
            Pair(it) {
                val button = Button(viewModel.context)
                button.text = context.translation[it.nameKey]

                button.setOnClickListener { _ ->
                    it.run()
                }
                ViewAppearanceHelper.applyTheme(button)
                button
            }
        }

        actions.forEach {
            addView(it.second())
        }
    }
}