package me.rhunk.snapenhance.core.ui.menu

import me.rhunk.snapenhance.core.ModContext

abstract class AbstractMenu {
    lateinit var context: ModContext

    open fun init() {}
}