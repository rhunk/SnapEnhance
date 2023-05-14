package me.rhunk.snapenhance.features.impl.ui.menus

import me.rhunk.snapenhance.ModContext

abstract class AbstractMenu() {
    lateinit var context: ModContext

    open fun init() {}
}