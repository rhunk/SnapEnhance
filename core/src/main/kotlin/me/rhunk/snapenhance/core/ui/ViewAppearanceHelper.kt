package me.rhunk.snapenhance.core.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.Shape
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.core.SnapEnhance
import me.rhunk.snapenhance.core.util.ktx.getDimens
import me.rhunk.snapenhance.core.util.ktx.getDimensFloat
import me.rhunk.snapenhance.core.util.ktx.getIdentifier
import me.rhunk.snapenhance.core.wrapper.impl.composer.ComposerViewNode
import kotlin.random.Random

fun View.applyTheme(componentWidth: Int? = null, hasRadius: Boolean = false, isAmoled: Boolean = true) {
    ViewAppearanceHelper.applyTheme(this, componentWidth, hasRadius, isAmoled)
}

private val foregroundDrawableListTag = Random.nextInt(0x7000000, 0x7FFFFFFF)

@Suppress("UNCHECKED_CAST")
private fun View.getForegroundDrawables(): MutableMap<String, Drawable> {
    return getTag(foregroundDrawableListTag) as? MutableMap<String, Drawable>
        ?: mutableMapOf<String, Drawable>().also {
        setTag(foregroundDrawableListTag, it)
    }
}

private fun View.updateForegroundDrawable() {
    foreground = ShapeDrawable(object: Shape() {
        override fun draw(canvas: Canvas, paint: Paint) {
            getForegroundDrawables().forEach { (_, drawable) ->
                drawable.draw(canvas)
            }
        }
    })
}

fun View.removeForegroundDrawable(tag: String) {
    getForegroundDrawables().remove(tag)?.let {
        updateForegroundDrawable()
    }
}

fun View.addForegroundDrawable(tag: String, drawable: Drawable) {
    getForegroundDrawables()[tag] = drawable
    updateForegroundDrawable()
}

fun View.triggerCloseTouchEvent() {
    arrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach {
        this.dispatchTouchEvent(
            MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                it, 0f, 0f, 0
            )
        )
    }
}

fun Activity.triggerRootCloseTouchEvent() {
    findViewById<View>(android.R.id.content).triggerCloseTouchEvent()
}

fun ViewGroup.children(): List<View> {
    val children = mutableListOf<View>()
    for (i in 0 until childCount) {
        children.add(getChildAt(i))
    }
    return children
}

fun View.iterateParent(predicate: (View) -> Boolean) {
    var parent = this.parent as? View ?: return
    while (true) {
        if (predicate(parent)) return
        parent = parent.parent as? View ?: return
    }
}

fun View.getComposerViewNode(): ComposerViewNode? {
    if (!this::class.java.isAssignableFrom(SnapEnhance.classCache.composerView)) return null
    val composerViewNode = this::class.java.methods.firstOrNull {
        it.name == "getComposerViewNode"
    }?.invoke(this) ?: return null

    return ComposerViewNode(composerViewNode::class.java.methods.firstOrNull {
        it.name == "getNativeHandle"
    }?.invoke(composerViewNode) as? Long ?: return null)
}

object ViewAppearanceHelper {
    private fun createRoundedBackground(color: Int, radius: Float, hasRadius: Boolean): Drawable {
        if (!hasRadius) return ColorDrawable(color)
        return ShapeDrawable().apply {
            paint.color = color
            shape = android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius),
                null,
                null
            )
        }
    }

    fun applyTheme(component: View, componentWidth: Int? = null, hasRadius: Boolean = false, isAmoled: Boolean = true) {
        val resources = component.context.resources
        val actionSheetCellHorizontalPadding = resources.getDimens("action_sheet_cell_horizontal_padding")
        val v11ActionCellVerticalPadding = resources.getDimens("v11_action_cell_vertical_padding")

        val sigColorTextPrimary = component.context.theme.obtainStyledAttributes(
            intArrayOf(resources.getIdentifier("sigColorTextPrimary", "attr"))
        ).getColor(0, 0)
        val sigColorBackgroundSurface = component.context.theme.obtainStyledAttributes(
            intArrayOf(resources.getIdentifier("sigColorBackgroundSurface", "attr"))
        ).getColor(0, 0)

        val actionSheetDefaultCellHeight = resources.getDimens("action_sheet_default_cell_height")
        val actionSheetCornerRadius = resources.getDimensFloat("action_sheet_corner_radius")
        val snapchatFontResId = resources.getIdentifier("avenir_next_medium", "font")

        (component as? TextView)?.apply {
            setTextColor(sigColorTextPrimary)
            setShadowLayer(0F, 0F, 0F, 0)
            gravity = Gravity.CENTER_VERTICAL
            componentWidth?.let { width = it}
            isAllCaps = false
            minimumHeight = actionSheetDefaultCellHeight
            textSize = 16f
            typeface = resources.getFont(snapchatFontResId)
            outlineProvider = null
            setPadding(actionSheetCellHorizontalPadding, v11ActionCellVerticalPadding, actionSheetCellHorizontalPadding, v11ActionCellVerticalPadding)
        }

        if (isAmoled) {
            component.background = StateListDrawable().apply {
                addState(intArrayOf(), createRoundedBackground(color = sigColorBackgroundSurface, radius = actionSheetCornerRadius, hasRadius))
                addState(intArrayOf(android.R.attr.state_pressed), createRoundedBackground(color = 0x5395026, radius = actionSheetCornerRadius, hasRadius))
            }
        } else {
            component.setBackgroundColor(0x0)
        }

        (component as? Switch)?.apply {
            switchMinWidth = resources.getDimens("v11_switch_min_width")
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
                ), intArrayOf(
                    Color.parseColor("#1d1d1d"),
                    Color.parseColor("#26bd49")
                )
            )
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
                ), intArrayOf(
                    Color.parseColor("#F5F5F5"),
                    Color.parseColor("#26bd49")
                )
            )
        }
    }

    fun newAlertDialogBuilder(context: Context?) = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
}
