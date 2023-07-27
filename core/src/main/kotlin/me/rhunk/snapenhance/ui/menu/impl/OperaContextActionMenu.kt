package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import me.rhunk.snapenhance.ui.ViewAppearanceHelper.applyTheme

@SuppressLint("DiscouragedApi")
class OperaContextActionMenu : AbstractMenu() {
    private val contextCardsScrollView by lazy {
        context.resources.getIdentifier("context_cards_scroll_view", "id", Constants.SNAPCHAT_PACKAGE_NAME)
    }

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
    fun inject(viewGroup: ViewGroup, childView: View) {
        try {
            if (viewGroup.parent !is ScrollView) return
            val parent = viewGroup.parent as ScrollView
            if (parent.id != contextCardsScrollView) return
            if (childView !is LinearLayout) return
            if (!isViewGroupButtonMenuContainer(childView as ViewGroup)) return

            val linearLayout = LinearLayout(childView.getContext())
            linearLayout.orientation = LinearLayout.VERTICAL
            linearLayout.gravity = Gravity.CENTER
            linearLayout.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            val button = Button(childView.getContext())
            button.text = context.translation["opera_context_menu.download"]
            button.setOnClickListener { context.feature(MediaDownloader::class).downloadLastOperaMediaAsync() }
            applyTheme(button)
            linearLayout.addView(button)
            (childView as ViewGroup).addView(linearLayout, 0)
        } catch (e: Throwable) {
            Logger.xposedLog(e)
        }
    }
}
