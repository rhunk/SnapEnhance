package me.rhunk.snapenhance.features.impl.ui.menus

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.Constants

object ViewAppearanceHelper {
    @SuppressLint("UseSwitchCompatOrMaterialCode", "RtlHardcoded", "DiscouragedApi",
        "ClickableViewAccessibility"
    )
    fun applyTheme(viewModel: View, view: TextView) {
        val sigColorTextPrimary = viewModel.context.theme.obtainStyledAttributes(
            intArrayOf(
                viewModel.resources.getIdentifier(
                    "sigColorTextPrimary",
                    "attr",
                    Constants.SNAPCHAT_PACKAGE_NAME
                )
            )
        )

        val snapchatFontResId = view.context.resources.getIdentifier("avenir_next_medium", "font", "com.snapchat.android")
        //remove the shadow
        view.setBackgroundColor(0x00000000)
        view.setTextColor(sigColorTextPrimary.getColor(0, 0))
        view.setShadowLayer(0F, 0F, 0F, 0)
        view.outlineProvider = null
        view.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        view.width = viewModel.width

        //DPI Calculator
        val scalingFactor = view.context.resources.displayMetrics.densityDpi.toDouble() / 400
        view.height = (150 * scalingFactor).toInt()
        view.setPadding((40 * scalingFactor).toInt(), 0, (40 * scalingFactor).toInt(), 0)
        view.isAllCaps = false
        view.textSize = 16f
        view.typeface = view.context.resources.getFont(snapchatFontResId)
        
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
