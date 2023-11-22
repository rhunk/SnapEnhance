package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.ui.applyTheme
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.wrapper.impl.ScSize
import java.text.DateFormat
import java.util.Date

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
            val translation = context.translation.getCategory("opera_context_menu")
            val mediaDownloader = context.feature(MediaDownloader::class)
            val paramMap = mediaDownloader.lastSeenMapParams

            if (paramMap != null && context.config.userInterface.operaMediaQuickInfo.get()) {
                val playableStorySnapRecord = paramMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
                val sentTimestamp = playableStorySnapRecord?.substringAfter("timestamp=")
                    ?.substringBefore(",")?.toLongOrNull()
                    ?: paramMap["MESSAGE_ID"]?.toString()?.let { messageId ->
                        context.database.getConversationMessageFromId(
                            messageId.substring(messageId.lastIndexOf(":") + 1)
                                .toLong()
                        )?.creationTimestamp
                    }
                    ?: paramMap["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull()
                val dateFormat = DateFormat.getDateTimeInstance()
                val creationTimestamp = playableStorySnapRecord?.substringAfter("creationTimestamp=")
                    ?.substringBefore(",")?.toLongOrNull()
                val expirationTimestamp = playableStorySnapRecord?.substringAfter("expirationTimestamp=")
                    ?.substringBefore(",")?.toLongOrNull()
                    ?: paramMap["SNAP_EXPIRATION_TIMESTAMP_MILLIS"]?.toString()?.toLongOrNull()

                val mediaSize = paramMap["snap_size"]?.let { ScSize(it) }
                val durationMs = paramMap["media_duration_ms"]?.toString()

                val stringBuilder = StringBuilder().apply {
                    if (sentTimestamp != null) {
                        append(translation.format("sent_at", "date" to dateFormat.format(Date(sentTimestamp))))
                        append("\n")
                    }
                    if (creationTimestamp != null) {
                        append(translation.format("created_at", "date" to dateFormat.format(Date(creationTimestamp))))
                        append("\n")
                    }
                    if (expirationTimestamp != null) {
                        append(translation.format("expires_at", "date" to dateFormat.format(Date(expirationTimestamp))))
                        append("\n")
                    }
                    if (mediaSize != null) {
                        append(translation.format("media_size", "size" to "${mediaSize.first}x${mediaSize.second}"))
                        append("\n")
                    }
                    if (durationMs != null) {
                        append(translation.format("media_duration", "duration" to durationMs))
                        append("\n")
                    }
                    if (last() == '\n') deleteCharAt(length - 1)
                }

                if (stringBuilder.isNotEmpty()) {
                    linearLayout.addView(TextView(view.context).apply {
                        text = stringBuilder.toString()
                        setPadding(40, 10, 0, 0)
                    })
                }
            }

            linearLayout.addView(Button(view.context).apply {
                text = translation["download"]
                setOnClickListener {
                    mediaDownloader.downloadLastOperaMediaAsync()
                    parentView.triggerCloseTouchEvent()
                }
                applyTheme(isAmoled = false)
            })

            if (context.isDeveloper) {
                linearLayout.addView(Button(view.context).apply {
                    text = translation["show_debug_info"]
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
