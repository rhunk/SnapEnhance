package me.rhunk.snapenhance.core.ui

import android.view.View
import kotlin.random.Random

class ViewTagState {
    private val tag = Random.nextInt(0x7000000, 0x7FFFFFFF)

    operator fun get(view: View) = hasState(view)

    private fun hasState(view: View): Boolean {
        if (view.getTag(tag) != null) return true
        view.setTag(tag, true)
        return false
    }

    fun removeState(view: View) {
        view.setTag(tag, null)
    }
}