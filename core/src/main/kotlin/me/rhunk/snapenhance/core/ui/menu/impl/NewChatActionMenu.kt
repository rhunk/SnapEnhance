package me.rhunk.snapenhance.core.ui.menu.impl

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.experiments.ConvertMessageLocally
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.ui.iterateParent
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.ktx.isDarkTheme
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.util.ktx.vibrateLongPress

class NewChatActionMenu : AbstractMenu() {
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
                        setObjectField("a", null) // remove drag callback
                        if (height < composeView.measuredHeight) {
                            height += composeView.measuredHeight
                        }
                    }
                    event.parent.requestLayout()
                }
                addView(event.view)
            })
        }
    }
}