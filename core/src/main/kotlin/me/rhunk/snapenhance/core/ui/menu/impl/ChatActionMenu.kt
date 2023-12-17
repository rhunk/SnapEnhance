package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.features.impl.experiments.ConvertMessageLocally
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.ViewTagState
import me.rhunk.snapenhance.core.ui.applyTheme
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getDimens
import java.time.Instant


@SuppressLint("DiscouragedApi")
class ChatActionMenu : AbstractMenu() {
    private val viewTagState = ViewTagState()

    private val defaultGap by lazy { context.resources.getDimens("default_gap") }

    private val chatActionMenuItemMargin by lazy { context.resources.getDimens("chat_action_menu_item_margin") }

    private val actionMenuItemHeight by lazy { context.resources.getDimens("action_menu_item_height") }

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
                this@ChatActionMenu.context.copyToClipboard(text, title)
            }
        }.show()
    }

    private val lastFocusedMessage
        get() = context.database.getConversationMessageFromId(context.feature(Messaging::class).lastFocusedMessageId)

    override fun init() {
        runCatching {
            if (!context.config.downloader.chatDownloadContextMenu.get() && context.config.messaging.messageLogger.globalState != true && !context.isDeveloper) return
            context.androidContext.classLoader.loadClass("com.snap.messaging.chat.features.actionmenu.ActionMenuChatItemContainer")
                .hook("onMeasure", HookStage.BEFORE) { param ->
                    param.setArg(1,
                        View.MeasureSpec.makeMeasureSpec((context.resources.displayMetrics.heightPixels * 0.35).toInt(), View.MeasureSpec.AT_MOST)
                    )
                }
        }.onFailure {
            context.log.error("Failed to hook ActionMenuChatItemContainer: $it")
        }
    }

    @SuppressLint("SetTextI18n", "DiscouragedApi", "ClickableViewAccessibility")
    override fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {
        val viewGroup = parent.parent.parent as? ViewGroup ?: return
        if (viewTagState[viewGroup]) return
        //close the action menu using a touch event
        val closeActionMenu = { parent.triggerCloseTouchEvent() }

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
                applyTheme(viewGroup.width, true)
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
        if (context.config.messaging.messageLogger.globalState == true) {
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

        if (context.config.experimental.convertMessageLocally.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.convert_message"]
                setOnClickListener {
                    closeActionMenu()
                    messaging.conversationManager?.fetchMessage(
                        messaging.openedConversationUUID.toString(),
                        messaging.lastFocusedMessageId,
                        onSuccess = {
                            this@ChatActionMenu.context.runOnUiThread {
                                runCatching {
                                    this@ChatActionMenu.context.feature(ConvertMessageLocally::class)
                                        .convertMessageInterface(it)
                                }.onFailure {
                                    this@ChatActionMenu.context.log.verbose("Failed to convert message: $it")
                                    this@ChatActionMenu.context.shortToast("Failed to edit message: $it")
                                }
                            }
                        },
                        onError = {
                            this@ChatActionMenu.context.shortToast("Failed to fetch message: $it")
                        }
                    )
                }
            })
        }

        if (context.isDeveloper) {
            viewGroup.addView(createContainer(viewGroup).apply {
                val debugText = StringBuilder()

                setOnClickListener {
                    this@ChatActionMenu.context.copyToClipboard(debugText.toString(), "debug")
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
                                append("parsed_content_type: ${
                                    ContentType.fromMessageContainer(
                                    ProtoReader(arroyoMessage.messageContent!!).followPath(4, 4)
                                ).let { "$it (${it?.id})" }}\n")
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

        viewGroup.addView(buttonContainer)
    }
}
