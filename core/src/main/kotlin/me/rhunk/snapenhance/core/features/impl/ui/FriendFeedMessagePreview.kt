package me.rhunk.snapenhance.core.features.impl.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.experiments.EndToEndEncryption
import me.rhunk.snapenhance.core.ui.addForegroundDrawable
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable
import me.rhunk.snapenhance.core.util.EvictingMap
import kotlin.math.absoluteValue

@SuppressLint("DiscouragedApi")
class FriendFeedMessagePreview : Feature("FriendFeedMessagePreview", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val sigColorTextPrimary by lazy {
        context.mainActivity!!.theme.obtainStyledAttributes(
            intArrayOf(context.resources.getIdentifier("sigColorTextPrimary", "attr", Constants.SNAPCHAT_PACKAGE_NAME))
        ).getColor(0, 0)
    }

    private val friendNameCache = EvictingMap<String, String>(100)

    private fun getDimens(name: String) = context.resources.getDimensionPixelSize(context.resources.getIdentifier(name, "dimen", Constants.SNAPCHAT_PACKAGE_NAME))

    override fun onActivityCreate() {
        val setting = context.config.userInterface.friendFeedMessagePreview
        if (setting.globalState != true) return

        val hasE2EE = context.config.experimental.e2eEncryption.globalState == true
        val endToEndEncryption by lazy { context.feature(EndToEndEncryption::class) }

        val ffItemId = context.resources.getIdentifier("ff_item", "id", Constants.SNAPCHAT_PACKAGE_NAME)

        val secondaryTextSize = getDimens("ff_feed_cell_secondary_text_size").toFloat()
        val ffSdlAvatarMargin = getDimens("ff_sdl_avatar_margin")
        val ffSdlAvatarSize = getDimens("ff_sdl_avatar_size")
        val ffSdlAvatarStartMargin = getDimens("ff_sdl_avatar_start_margin")
        val ffSdlPrimaryTextStartMargin = getDimens("ff_sdl_primary_text_start_margin").toFloat()

        val feedEntryHeight = ffSdlAvatarSize + ffSdlAvatarMargin * 2 + ffSdlAvatarStartMargin
        val separatorHeight = (context.resources.displayMetrics.density * 2).toInt()
        val textPaint = TextPaint().apply {
            textSize = secondaryTextSize
        }

        context.event.subscribe(BindViewEvent::class) { param ->
            param.friendFeedItem { conversationId ->
                val frameLayout = param.view as ViewGroup
                val ffItem = frameLayout.findViewById<View>(ffItemId)

                ffItem.layoutParams = ffItem.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                frameLayout.removeForegroundDrawable("ffItem")

                val stringMessages = context.database.getMessagesFromConversationId(conversationId, setting.amount.get().absoluteValue)?.mapNotNull { message ->
                    val messageContainer =
                        message.messageContent
                            ?.let { ProtoReader(it) }
                            ?.followPath(4, 4)?.let { messageReader ->
                                takeIf { hasE2EE }?.let takeIf@{
                                    endToEndEncryption.tryDecryptMessage(
                                        senderId = message.senderId ?: return@takeIf null,
                                        clientMessageId = message.clientMessageId.toLong(),
                                        conversationId =  message.clientConversationId ?: return@takeIf null,
                                        contentType = ContentType.fromId(message.contentType),
                                        messageBuffer = messageReader.getBuffer()
                                    ).second
                                }?.let { ProtoReader(it) } ?: messageReader
                            }
                        ?: return@mapNotNull null

                    val messageString = messageContainer.getString(2, 1)
                        ?: ContentType.fromMessageContainer(messageContainer)?.name
                        ?: return@mapNotNull null

                    val friendName = friendNameCache.getOrPut(message.senderId ?: return@mapNotNull null) {
                        context.database.getFriendInfo(message.senderId ?: return@mapNotNull null)?.let { it.displayName?: it.mutableUsername } ?: "Unknown"
                    }
                    "$friendName: $messageString"
                }?.reversed() ?: return@friendFeedItem

                var maxTextHeight = 0
                val previewContainerHeight = stringMessages.sumOf { msg ->
                    val rect = Rect()
                    textPaint.getTextBounds(msg, 0, msg.length, rect)
                    rect.height().also {
                        if (it > maxTextHeight) maxTextHeight = it
                    }.plus(separatorHeight)
                }

                ffItem.layoutParams = ffItem.layoutParams.apply {
                    height = feedEntryHeight + previewContainerHeight + separatorHeight
                }

                frameLayout.addForegroundDrawable("ffItem", ShapeDrawable(object: Shape() {
                    override fun draw(canvas: Canvas, paint: Paint) {
                        val offsetY = canvas.height.toFloat() - previewContainerHeight

                        stringMessages.forEachIndexed { index, messageString ->
                            paint.textSize = secondaryTextSize
                            paint.color = sigColorTextPrimary
                            canvas.drawText(messageString,
                                feedEntryHeight + ffSdlPrimaryTextStartMargin,
                                offsetY + index * maxTextHeight,
                                paint
                            )
                        }
                    }
                }))
            }
        }
    }
}