package me.rhunk.snapenhance.features.impl.ui.menus

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.Constants

object ViewAppearanceHelper {
    fun applyDimension(size: Float, context: Context, margins: Boolean = true): Float {
        return(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, context.resources.displayMetrics) + (if (margins) 0.5f else 0.0f));
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode", "RtlHardcoded", "DiscouragedApi",
        "ClickableViewAccessibility"
    )
    fun applyTheme(viewModel: View, view: View) {
        val snapchatFontResId = view.context.resources.getIdentifier("avenir_next_medium", "font", "com.snapchat.android")
        //remove the shadow
        view.setBackgroundColor(0x00000000)

        //DPI Calculator
        val scalingFactor = view.context.resources.displayMetrics.densityDpi.toDouble() / 400
        view.setPadding((40 * scalingFactor).toInt(), 0, (40 * scalingFactor).toInt(), 0)

        if (view is TextView) {
            view.apply {
                height = (150 * scalingFactor).toInt()
                isAllCaps = false
                textSize = 16f
                typeface = context.resources.getFont(snapchatFontResId)

                setTextColor(resources.getColor(resources.getIdentifier("sig_color_text_primary_light", "color", Constants.SNAPCHAT_PACKAGE_NAME), null))
                setShadowLayer(0F, 0F, 0F, 0)
                outlineProvider = null
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                width = viewModel.width
            }
        }

        
        //FIXME: wrong color, shouldn't be that much noticeable though
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.setBackgroundColor(0x5395026)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.setBackgroundColor(0x00000000)
                }
            }
            false
        }
        
        if (view is Switch) {
            with(viewModel.resources) {
                view.switchMinWidth = getDimension(getIdentifier("v11_switch_min_width", "dimen", Constants.SNAPCHAT_PACKAGE_NAME)).toInt()
            }
            val colorStateList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
                ), intArrayOf(
                    Color.parseColor("#1d1d1d"),
                    Color.parseColor("#26bd49")
                )
            )
            val thumbStateList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
                ), intArrayOf(
                    Color.parseColor("#F5F5F5"),
                    Color.parseColor("#26bd49")
                )
            )
            view.trackTintList = colorStateList
            view.thumbTintList = thumbStateList
        }
    }
}
