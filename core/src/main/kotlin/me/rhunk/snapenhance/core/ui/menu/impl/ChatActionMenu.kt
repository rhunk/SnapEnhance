package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.experiments.ConvertMessageLocally
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.ViewTagState
import me.rhunk.snapenhance.core.ui.applyTheme
import me.rhunk.snapenhance.core.ui.debugEditText
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getDimens
import java.text.SimpleDateFormat
import java.util.Date


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

    private fun debugAlertDialog(context: Context, title: String, text: String) {
        this@ChatActionMenu.context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context).apply {
                setTitle(title)
                setView(debugEditText(context, text))
                setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                setNegativeButton("Copy") { _, _ ->
                    this@ChatActionMenu.context.copyToClipboard(text, title)
                }
            }.show()
        }
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

    @OptIn(ExperimentalLayoutApi::class)
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
            val composeDebugView = createComposeView(viewGroup.context) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        debugAlertDialog(viewGroup.context,
                            "Message Info",
                            StringBuilder().apply {
                                runCatching {
                                    append("conversation_id: ${arroyoMessage.clientConversationId}\n")
                                    append("sender_id: ${arroyoMessage.senderId}\n")
                                    append("client_id: ${arroyoMessage.clientMessageId}, server_id: ${arroyoMessage.serverMessageId}\n")
                                    append("content_type: ${ContentType.fromId(arroyoMessage.contentType)} (${arroyoMessage.contentType})\n")
                                    append("parsed_content_type: ${
                                        ContentType.fromMessageContainer(
                                            ProtoReader(arroyoMessage.messageContent!!).followPath(4, 4)
                                        ).let { "$it (${it?.id})" }}\n")
                                    append("creation_timestamp: ${
                                        SimpleDateFormat.getDateTimeInstance().format(
                                            Date(arroyoMessage.creationTimestamp)
                                        )} (${arroyoMessage.creationTimestamp})\n")
                                    append("read_timestamp: ${SimpleDateFormat.getDateTimeInstance().format(
                                        Date(arroyoMessage.readTimestamp)
                                    )} (${arroyoMessage.readTimestamp})\n")
                                    append("ml_deleted: ${messageLogger.isMessageDeleted(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong())}, ")
                                    append("ml_stored: ${messageLogger.getMessageObject(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong()) != null}\n")
                                }
                            }.toString()
                        )
                    }) {
                        Text("Info")
                    }
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        messaging.conversationManager?.fetchMessage(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong(), onSuccess = { message ->
                            val decodedAttachments = MessageDecoder.decode(message.messageContent!!)

                            debugAlertDialog(
                                viewGroup.context,
                                "Media References",
                                decodedAttachments.mapIndexed { index, attachment ->
                                    StringBuilder().apply {
                                        append("---- media $index ----\n")
                                        append("resolveProto: ${attachment.mediaUrlKey}\n")
                                        append("type: ${attachment.type}\n")
                                        attachment.attachmentInfo?.apply {
                                            encryption?.let {
                                                append("encryption:\n  - key: ${it.key}\n  - iv: ${it.iv}\n")
                                            }
                                            resolution?.let {
                                                append("resolution: ${it.first}x${it.second}\n")
                                            }
                                            duration?.let {
                                                append("duration: $it\n")
                                            }
                                        }
                                    }.toString()
                                }.joinToString("\n\n")
                            )
                        })
                    }) {
                        Text("Refs")
                    }
                    Button(onClick = {
                        val message = lastFocusedMessage ?: return@Button
                        debugAlertDialog(
                            viewGroup.context,
                            "Arroyo proto",
                            message.messageContent?.let { ProtoReader(it) }?.toString() ?: "empty"
                        )
                    }) {
                        Text("Arroyo proto")
                    }
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        messaging.conversationManager?.fetchMessage(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong(), onSuccess = { message ->
                            debugAlertDialog(
                                viewGroup.context,
                                "Message proto",
                                message.messageContent?.content?.let { ProtoReader(it) }?.toString() ?: "empty"
                            )
                        }, onError = {
                            this@ChatActionMenu.context.shortToast("Failed to fetch message: $it")
                        })
                    }) {
                        Text("Message proto")
                    }
                }
            }
            viewGroup.addView(createContainer(viewGroup).apply {
                addView(composeDebugView)
            })
        }

        viewGroup.addView(buttonContainer)
    }
}
