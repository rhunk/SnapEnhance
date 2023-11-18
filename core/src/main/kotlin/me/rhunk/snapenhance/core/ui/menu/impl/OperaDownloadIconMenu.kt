package me.rhunk.snapenhance.core.ui.menu.impl

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.ui.children
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.util.ktx.getDimens
import me.rhunk.snapenhance.core.util.ktx.getDrawable

class OperaDownloadIconMenu : AbstractMenu() {
    private val downloadSvgDrawable by lazy { context.resources.getDrawable("svg_download", context.androidContext.theme) }
    private val actionMenuIconSize by lazy { context.resources.getDimens("action_menu_icon_size") }
    private val actionMenuIconMargin by lazy { context.resources.getDimens("action_menu_icon_margin") }
    private val actionMenuIconMarginTop by lazy { context.resources.getDimens("action_menu_icon_margin_top") }

    override fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {
        if (!context.config.downloader.operaDownloadButton.get()) return

        parent.addView(ImageView(view.context).apply {
            setImageDrawable(downloadSvgDrawable)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                actionMenuIconSize,
                actionMenuIconSize
            ).apply {
                setMargins(0, actionMenuIconMarginTop * 2 + actionMenuIconSize, 0, 0)
                marginEnd = actionMenuIconMargin
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener {
                this@OperaDownloadIconMenu.context.feature(MediaDownloader::class).downloadLastOperaMediaAsync()
            }
            addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.visibility = View.VISIBLE
                    (parent.parent as? ViewGroup)?.children()?.forEach { child ->
                        if (child !is ViewGroup) return@forEach
                        child.children().forEach {
                            if (it::class.java.name.endsWith("PreviewToolbar")) v.visibility = View.GONE
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })
        }, 0)
    }
}