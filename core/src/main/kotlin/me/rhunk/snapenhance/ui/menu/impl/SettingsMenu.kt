package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.view.View
import android.widget.Button
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.ui.menu.AbstractMenu

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