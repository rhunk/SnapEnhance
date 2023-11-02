package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.ui.applyTheme
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.util.ktx.getId

@SuppressLint("DiscouragedApi")
class OperaContextActionMenu : AbstractMenu() {
    private val contextCardsScrollView by lazy { context.resources.getId("context_cards_scroll_view") }

    /*
    LinearLayout :
        - LinearLayout:
            - SnapFontTextView
            - ImageView
        - LinearLayout:
            - SnapFontTextView
            - ImageView
        - LinearLayout:
            - SnapFontTextView
            - ImageView
     */
    private fun isViewGroupButtonMenuContainer(viewGroup: ViewGroup): Boolean {
        if (viewGroup !is LinearLayout) return false
        val children = ArrayList<View>()
        for (i in 0 until viewGroup.getChildCount())
            children.add(viewGroup.getChildAt(i))
        return if (children.any { view: View? -> view !is LinearLayout })
            false
        else children.map { view: View -> view as LinearLayout }
            .any { linearLayout: LinearLayout ->
                val viewChildren = ArrayList<View>()
                for (i in 0 until linearLayout.childCount) viewChildren.add(
                    linearLayout.getChildAt(
                        i
                    )
                )
                viewChildren.any { viewChild: View ->
                    viewChild.javaClass.name.endsWith("SnapFontTextView")
                }
            }
    }

    @SuppressLint("SetTextI18n")
    override fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {
        try {
            if (parent.parent !is ScrollView) return
            val parentView = parent.parent as ScrollView
            if (parentView.id != contextCardsScrollView) return
            if (view !is LinearLayout) return
            if (!isViewGroupButtonMenuContainer(view as ViewGroup)) return

            val linearLayout = LinearLayout(view.context)
            linearLayout.orientation = LinearLayout.VERTICAL
            linearLayout.gravity = Gravity.CENTER
            linearLayout.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            val translation = context.translation
            val mediaDownloader = context.feature(MediaDownloader::class)

            linearLayout.addView(Button(view.context).apply {
                text = translation["opera_context_menu.download"]
                setOnClickListener { mediaDownloader.downloadLastOperaMediaAsync() }
                applyTheme(isAmoled = false)
            })

            if (context.isDeveloper) {
                linearLayout.addView(Button(view.context).apply {
                    text = "Show debug info"
                    setOnClickListener { mediaDownloader.showLastOperaDebugMediaInfo() }
                    applyTheme(isAmoled = false)
                })
            }

            (view as ViewGroup).addView(linearLayout, 0)
        } catch (e: Throwable) {
            context.log.error("Error while injecting OperaContextActionMenu", e)
        }
    }
}
