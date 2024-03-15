package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.util.ktx.getDimens
import me.rhunk.snapenhance.core.util.ktx.getDrawable
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.util.ktx.getStyledAttributes


@SuppressLint("DiscouragedApi")
class SettingsGearInjector : AbstractMenu() {
    override fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {
        if (context.config.userInterface.hideSettingsGear.get()) return
        val firstView = (view as ViewGroup).getChildAt(0)

        val ngsHovaHeaderSearchIconBackgroundMarginLeft = context.resources.getDimens("ngs_hova_header_search_icon_background_margin_left")

        view.clipChildren = false
        view.addView(FrameLayout(parent.context).apply {
            visibility = View.GONE
            post {
                layoutParams = FrameLayout.LayoutParams(firstView.layoutParams.width, firstView.layoutParams.height).apply {
                    y = 0f
                    x = if (parent.findViewById<View>(context.resources.getId("hova_nav_map_icon")) != null) {
                        parent.resources.displayMetrics.widthPixels - firstView.layoutParams.width - ngsHovaHeaderSearchIconBackgroundMarginLeft * 2 - (firstView.layoutParams.width).toFloat() * 2f
                    } else {
                        -(ngsHovaHeaderSearchIconBackgroundMarginLeft + firstView.layoutParams.width).toFloat()
                    }
                }
                visibility = View.VISIBLE
            }

            isClickable = true

            setOnClickListener {
                this@SettingsGearInjector.context.bridgeClient.openSettingsOverlay()
            }

            parent.setOnTouchListener { _, event ->
                if (view.visibility == View.INVISIBLE || view.alpha == 0F) return@setOnTouchListener false

                val viewLocation = IntArray(2)
                getLocationOnScreen(viewLocation)

                val x = event.rawX - viewLocation[0]
                val y = event.rawY - viewLocation[1]

                if (x > 0 && x < width && y > 0 && y < height) {
                    performClick()
                }

                false
            }
            backgroundTintList = firstView.backgroundTintList
            background = firstView.background

            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 17).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageDrawable(context.resources.getDrawable("svg_settings_32x32", context.theme))
                context.resources.getStyledAttributes("headerButtonOpaqueIconTint", context.theme).getColorStateList(0)?.let {
                    imageTintList = it
                }
            })
        })
    }
}