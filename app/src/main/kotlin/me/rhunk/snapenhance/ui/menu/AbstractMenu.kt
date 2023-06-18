package me.rhunk.snapenhance.ui.menu

import me.rhunk.snapenhance.ModContext

abstract class AbstractMenu() {
    lateinit var context: ModContext

    open fun init() {}
}