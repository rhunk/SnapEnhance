package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import me.rhunk.snapenhance.Constants.VIEW_INJECTED_CODE
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import me.rhunk.snapenhance.ui.menu.ViewAppearanceHelper


class ChatActionMenu : AbstractMenu() {
    private fun wasInjectedView(view: View): Boolean {
        if (view.getTag(VIEW_INJECTED_CODE) != null) return true
        view.setTag(VIEW_INJECTED_CODE, true)
        return false
    }

    @SuppressLint("SetTextI18n")
    fun inject(viewGroup: ViewGroup) {
        val parent = viewGroup.parent.parent as ViewGroup
        if (wasInjectedView(parent)) return
        //close the action menu using a touch event
        val closeActionMenu = {
            viewGroup.dispatchTouchEvent(
                MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_DOWN,
                    0f,
                    0f,
                    0
                )
            )
        }

        val injectButton = { button: Button ->
            ViewAppearanceHelper.applyTheme(button, parent.width, hasRadius = true)

            with(button) {
                layoutParams = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, 0, 40, 15)
                }
                parent.addView(this)
            }
        }

        if (context.config.bool(ConfigProperty.CHAT_DOWNLOAD_CONTEXT_MENU)) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.preview_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync { this@ChatActionMenu.context.feature(MediaDownloader::class).onMessageActionMenu(true) }
                }
            })

            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.download_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        this@ChatActionMenu.context.feature(
                            MediaDownloader::class
                        ).onMessageActionMenu(false)
                    }
                }
            })
        }

        //delete logged message button
        if (context.config.bool(ConfigProperty.MESSAGE_LOGGER)) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.delete_logged_message_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        with(this@ChatActionMenu.context.feature(Messaging::class)) {
                            context.feature(MessageLogger::class).deleteMessage(openedConversationUUID.toString(), lastFocusedMessageId)
                        }
                    }
                }
            })
        }
    }
}
