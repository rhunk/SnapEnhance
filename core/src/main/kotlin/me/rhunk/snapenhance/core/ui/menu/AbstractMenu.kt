package me.rhunk.snapenhance.core.ui.menu

import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.core.ModContext

abstract class AbstractMenu {
    lateinit var context: ModContext

    open fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {}

    open fun init() {}
}