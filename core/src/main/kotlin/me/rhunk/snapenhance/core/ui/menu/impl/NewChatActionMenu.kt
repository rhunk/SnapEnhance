package me.rhunk.snapenhance.core.ui.menu.impl

import android.content.Context
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.common.util.ktx.copyToClipboard
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.RemoteMediaResolver
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.experiments.ConvertMessageLocally
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.debugEditText
import me.rhunk.snapenhance.core.ui.iterateParent
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.ktx.isDarkTheme
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.util.ktx.vibrateLongPress
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class NewChatActionMenu : AbstractMenu() {
    private fun debugAlertDialog(context: Context, title: String, text: String) {
        this@NewChatActionMenu.context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context).apply {
                setTitle(title)
                setView(debugEditText(context, text))
                setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                setNegativeButton("Copy") { _, _ ->
                    context.copyToClipboard(text, title)
                }
            }.show()
        }
    }

    private val lastFocusedMessage
        get() = context.database.getConversationMessageFromId(context.feature(Messaging::class).lastFocusedMessageId)

    @OptIn(ExperimentalLayoutApi::class, ExperimentalEncodingApi::class)
    fun createDebugInfoView(context: Context): ComposeView {
        val messageLogger = this@NewChatActionMenu.context.feature(MessageLogger::class)
        val messaging = this@NewChatActionMenu.context.feature(Messaging::class)

        return createComposeView(context) {
            Card(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        debugAlertDialog(context,
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
                                    append("read_timestamp: ${
                                        SimpleDateFormat.getDateTimeInstance().format(
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
                                context,
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
                                        runCatching {
                                            val mediaHeaders = RemoteMediaResolver.getMediaHeaders(
                                                Base64.UrlSafe.decode(attachment.mediaUrlKey ?: return@runCatching))
                                            append("content-type: ${mediaHeaders["content-type"]}\n")
                                            append("content-length: ${Formatter.formatShortFileSize(context, mediaHeaders["content-length"]?.toLongOrNull() ?: 0)}\n")
                                            append("creation-date: ${mediaHeaders["last-modified"]}\n")
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
                            context,
                            "Arroyo proto",
                            message.messageContent?.let { ProtoReader(it) }?.toString() ?: "empty"
                        )
                    }) {
                        Text("Arroyo")
                    }
                    Button(onClick = {
                        val arroyoMessage = lastFocusedMessage ?: return@Button
                        messaging.conversationManager?.fetchMessage(arroyoMessage.clientConversationId!!, arroyoMessage.clientMessageId.toLong(), onSuccess = { message ->
                            debugAlertDialog(
                                context,
                                "Message proto",
                                message.messageContent?.content?.let { ProtoReader(it) }?.toString() ?: "empty"
                            )
                        }, onError = {
                            this@NewChatActionMenu.context.shortToast("Failed to fetch message: $it")
                        })
                    }) {
                        Text("Message")
                    }
                }
            }
        }
    }

    fun handle(event: AddViewEvent) {
        if (event.parent is LinearLayout) return
        val closeActionMenu = { event.parent.iterateParent {
            it.triggerCloseTouchEvent()
            false
        } }

        val mediaDownloader = context.feature(MediaDownloader::class)
        val messageLogger = context.feature(MessageLogger::class)
        val messaging = context.feature(Messaging::class)

        val composeView = createComposeView(event.view.context) {
            val primaryColor = remember { if (event.view.context.isDarkTheme()) Color.White else Color.Black }

            @Composable
            fun ListButton(
                modifier: Modifier = Modifier,
                icon: ImageVector,
                text: String,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(modifier)
                        .padding(top = 11.dp, bottom = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 16.dp),
                        imageVector = icon,
                        tint = primaryColor,
                        contentDescription = text
                    )
                    Text(text, color = primaryColor)
                }
                Spacer(modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (context.config.downloader.downloadContextMenu.get()) {
                    ListButton(icon = Icons.Default.RemoveRedEye, text = context.translation["chat_action_menu.preview_button"], modifier = Modifier.clickable {
                        closeActionMenu()
                        mediaDownloader.onMessageActionMenu(true)
                    })
                    ListButton(icon = Icons.Default.Download, text = context.translation["chat_action_menu.download_button"], modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                closeActionMenu()
                                mediaDownloader.onMessageActionMenu(false)
                            },
                            onLongPress = {
                                context.androidContext.vibrateLongPress()
                                mediaDownloader.onMessageActionMenu(isPreviewMode = false, forceAllowDuplicate = true)
                            }
                        )
                    })
                }

                if (context.config.messaging.messageLogger.globalState == true) {
                    ListButton(icon = Icons.Rounded.BookmarkRemove, text = context.translation["chat_action_menu.delete_logged_message_button"], modifier = Modifier.clickable {
                        closeActionMenu()
                        context.executeAsync {
                            messageLogger.deleteMessage(messaging.openedConversationUUID.toString(), messaging.lastFocusedMessageId)
                        }
                    })
                }

                if (context.config.experimental.convertMessageLocally.get()) {
                    ListButton(icon = Icons.Outlined.Image, text = context.translation["chat_action_menu.convert_message"], modifier = Modifier.clickable {
                        closeActionMenu()
                        messaging.conversationManager?.fetchMessage(
                            messaging.openedConversationUUID.toString(),
                            messaging.lastFocusedMessageId,
                            onSuccess = {
                                context.runOnUiThread {
                                    runCatching {
                                        context.feature(ConvertMessageLocally::class)
                                            .convertMessageInterface(it)
                                    }.onFailure {
                                        context.log.verbose("Failed to convert message: $it")
                                        context.shortToast("Failed to edit message: $it")
                                    }
                                }
                            },
                            onError = {
                                context.shortToast("Failed to fetch message: $it")
                            }
                        )
                    })
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        event.view = ScrollView(event.view.context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(composeView)
                composeView.post {
                    (event.parent.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        if (height < composeView.measuredHeight) {
                            height += composeView.measuredHeight
                        } else {
                            setObjectField("a", null) // remove drag callback
                        }
                    }
                    event.parent.requestLayout()
                }
                addView(event.view)
            })
        }
    }
}