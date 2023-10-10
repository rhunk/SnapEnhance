package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.view.View
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu

class SettingsMenu : AbstractMenu() {
    //TODO: quick settings
    @SuppressLint("SetTextI18n")
    @Suppress("UNUSED_PARAMETER")
    fun inject(viewModel: View, addView: (View) -> Unit) {
        /*val actions = context.actionManager.getActions().map {
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
        }*/
    }
}