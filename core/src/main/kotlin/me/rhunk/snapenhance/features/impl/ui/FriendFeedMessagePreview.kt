package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.ui.addForegroundDrawable
import me.rhunk.snapenhance.ui.removeForegroundDrawable
import kotlin.math.absoluteValue

@SuppressLint("DiscouragedApi")
class FriendFeedMessagePreview : Feature("FriendFeedMessagePreview", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val sigColorTextPrimary by lazy {
        context.mainActivity!!.theme.obtainStyledAttributes(
            intArrayOf(context.resources.getIdentifier("sigColorTextPrimary", "attr", Constants.SNAPCHAT_PACKAGE_NAME))
        ).getColor(0, 0)
    }

    private fun getDimens(name: String) = context.resources.getDimensionPixelSize(context.resources.getIdentifier(name, "dimen", Constants.SNAPCHAT_PACKAGE_NAME))

    override fun onActivityCreate() {
        val setting = context.config.userInterface.friendFeedMessagePreview
        if (setting.globalState != true) return

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
                    val messageContainer = message.messageContent
                        ?.let { ProtoReader(it) }
                        ?.followPath(4, 4)
                        ?: return@mapNotNull null

                    val messageString = messageContainer.getString(2, 1)
                        ?: ContentType.fromMessageContainer(messageContainer)?.name
                        ?: return@mapNotNull null

                    val friendName = context.database.getFriendInfo(message.senderId ?: return@mapNotNull null)?.let { it.displayName?: it.mutableUsername } ?: "Unknown"

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