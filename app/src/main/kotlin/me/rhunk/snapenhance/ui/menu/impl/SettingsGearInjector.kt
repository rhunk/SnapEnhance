package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.ui.config.ConfigActivity
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import java.io.File


@SuppressLint("DiscouragedApi")
class SettingsGearInjector : AbstractMenu() {
    private val headerButtonOpaqueIconTint by lazy {
        context.resources.getIdentifier("headerButtonOpaqueIconTint", "attr", Constants.SNAPCHAT_PACKAGE_NAME).let {
            context.androidContext.theme.obtainStyledAttributes(intArrayOf(it)).getColorStateList(0)
        }
    }

    private val settingsSvg by lazy {
        context.resources.getIdentifier("svg_settings_32x32", "drawable", Constants.SNAPCHAT_PACKAGE_NAME).let {
            context.resources.getDrawable(it, context.androidContext.theme)
        }
    }

    private val ngsHovaHeaderSearchIconBackgroundMarginLeft by lazy {
        context.resources.getIdentifier("ngs_hova_header_search_icon_background_margin_left", "dimen", Constants.SNAPCHAT_PACKAGE_NAME).let {
            context.resources.getDimensionPixelSize(it)
        }
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    fun inject(parent: ViewGroup, child: View) {
        val firstView = (child as ViewGroup).getChildAt(0)

        child.clipChildren = false
        child.addView(FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(firstView.layoutParams.width, firstView.layoutParams.height).apply {
                y = 0f
                x = -(ngsHovaHeaderSearchIconBackgroundMarginLeft + firstView.layoutParams.width).toFloat()
            }

            isClickable = true

            setOnClickListener {
                val intent = Intent().apply {
                    setClassName(BuildConfig.APPLICATION_ID, ConfigActivity::class.java.name)
                }
                intent.putExtra("lspatched", File(context.cacheDir, "lspatch/origin").exists())
                context.startActivity(intent)
            }

            parent.setOnTouchListener { _, event ->
                if (child.visibility == View.INVISIBLE || child.alpha == 0F) return@setOnTouchListener false

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
                setImageDrawable(settingsSvg)
                headerButtonOpaqueIconTint?.let {
                    imageTintList = it
                }
            })
        })
    }
}