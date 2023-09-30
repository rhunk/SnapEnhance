package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.ui.ViewTagState
import me.rhunk.snapenhance.ui.applyTheme
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import java.time.Instant


@SuppressLint("DiscouragedApi")
class ChatActionMenu : AbstractMenu() {
    private val viewTagState = ViewTagState()

    private val defaultGap by lazy {
        context.androidContext.resources.getDimensionPixelSize(
            context.androidContext.resources.getIdentifier(
                "default_gap",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )
    }

    private val chatActionMenuItemMargin by lazy {
        context.androidContext.resources.getDimensionPixelSize(
            context.androidContext.resources.getIdentifier(
                "chat_action_menu_item_margin",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )
    }

    private val actionMenuItemHeight by lazy {
        context.androidContext.resources.getDimensionPixelSize(
            context.androidContext.resources.getIdentifier(
                "action_menu_item_height",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )
    }

    private fun createContainer(viewGroup: ViewGroup): LinearLayout {
        val parent = viewGroup.parent.parent as ViewGroup

        return LinearLayout(viewGroup.context).apply layout@{
            orientation = LinearLayout.VERTICAL
            layoutParams = MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                applyTheme(parent.width, true)
                setMargins(chatActionMenuItemMargin, 0, chatActionMenuItemMargin, defaultGap)
            }
        }
    }

    private fun copyAlertDialog(context: Context, title: String, text: String) {
        ViewAppearanceHelper.newAlertDialogBuilder(context).apply {
            setTitle(title)
            setMessage(text)
            setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            setNegativeButton("Copy") { _, _ ->
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("debug", text))
            }
        }.show()
    }

    private val lastFocusedMessage
        get() = context.database.getConversationMessageFromId(context.feature(Messaging::class).lastFocusedMessageId)

    @SuppressLint("SetTextI18n", "DiscouragedApi", "ClickableViewAccessibility")
    fun inject(viewGroup: ViewGroup) {
        val parent = viewGroup.parent.parent as? ViewGroup ?: return
        if (viewTagState[parent]) return
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

        val messaging = context.feature(Messaging::class)
        val messageLogger = context.feature(MessageLogger::class)

        val buttonContainer = createContainer(viewGroup)

        val injectButton = { button: Button ->
            if (buttonContainer.childCount > 0) {
                buttonContainer.addView(View(viewGroup.context).apply {
                    layoutParams = MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        height = 1
                    }
                    setBackgroundColor(0x1A000000)
                })
            }

            with(button) {
                applyTheme(parent.width, true)
                layoutParams = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    height = actionMenuItemHeight + defaultGap
                }
                buttonContainer.addView(this)
            }
        }

        if (context.config.downloader.chatDownloadContextMenu.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.preview_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync { feature(MediaDownloader::class).onMessageActionMenu(true) }
                }
            })

            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.download_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        feature(MediaDownloader::class).onMessageActionMenu(false)
                    }
                }
            })
        }

        //delete logged message button
        if (context.config.messaging.messageLogger.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.delete_logged_message_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        messageLogger.deleteMessage(messaging.openedConversationUUID.toString(), messaging.lastFocusedMessageId)
                    }
                }
            })
        }

        if (context.isDeveloper) {
            parent.addView(createContainer(viewGroup).apply {
                val debugText = StringBuilder()

                setOnClickListener {
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("debug", debugText.toString()))
                }

                addView(TextView(viewGroup.context).apply {
                    setPadding(20, 20, 20, 20)
                    textSize = 10f
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        val arroyoMessage = lastFocusedMessage ?: return@addOnLayoutChangeListener
                        text = debugText.apply {
                            runCatching {
                                clear()
                                append("sender_id: ${arroyoMessage.senderId}\n")
                                append("client_id: ${arroyoMessage.clientMessageId}, server_id: ${arroyoMessage.serverMessageId}\n")
                                append("conversation_id: ${arroyoMessage.clientConversationId}\n")
                                append("arroyo_content_type: ${ContentType.fromId(arroyoMessage.contentType)} (${arroyoMessage.contentType})\n")
                                append("parsed_content_type: ${ContentType.fromMessageContainer(
                                    ProtoReader(arroyoMessage.messageContent!!).followPath(4, 4)
                                ).let { "$it (${it.id})" }}\n")
                                append("creation_timestamp: ${arroyoMessage.creationTimestamp} (${Instant.ofEpochMilli(arroyoMessage.creationTimestamp)})\n")
                                append("read_timestamp: ${arroyoMessage.readTimestamp} (${Instant.ofEpochMilli(arroyoMessage.readTimestamp)})\n")
                                append("is_messagelogger_deleted: ${messageLogger.isMessageDeleted(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong())}\n")
                                append("is_messagelogger_stored: ${messageLogger.getMessageObject(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong()) != null}\n")
                            }.onFailure {
                                debugText.append("Error: $it\n")
                            }
                        }.toString().trimEnd()
                    }
                })

                // action buttons
                addView(LinearLayout(viewGroup.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(Button(viewGroup.context).apply {
                        text = "Show Deleted Message Object"
                        setOnClickListener {
                            val message = lastFocusedMessage ?: return@setOnClickListener
                            copyAlertDialog(
                                viewGroup.context,
                                "Deleted Message Object",
                                messageLogger.getMessageObject(message.clientConversationId!!, message.clientMessageId.toLong())?.toString()
                                    ?: "null"
                            )
                        }
                    })
                })
            })
        }

        parent.addView(buttonContainer)
    }
}
