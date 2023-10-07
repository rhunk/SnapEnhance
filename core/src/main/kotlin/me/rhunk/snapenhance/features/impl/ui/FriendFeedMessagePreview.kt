package me.rhunk.snapenhance.features.impl.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.ui.addForegroundDrawable
import me.rhunk.snapenhance.ui.removeForegroundDrawable

class FriendFeedMessagePreview : Feature("FriendFeedMessagePreview", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    @SuppressLint("SetTextI18n", "DiscouragedApi")
    override fun onActivityCreate() {
        if (!context.config.userInterface.conversationPreviewInFriendFeed.get()) return

        val ffItemId = context.resources.getIdentifier("ff_item", "id", Constants.SNAPCHAT_PACKAGE_NAME)

        val sigColorTextPrimary by lazy {
            context.mainActivity!!.theme.obtainStyledAttributes(
                intArrayOf(context.resources.getIdentifier("sigColorTextPrimary", "attr", Constants.SNAPCHAT_PACKAGE_NAME))
            ).getColor(0, 0)
        }

        val ffSdlAvatarMargin = context.resources.getDimensionPixelSize(context.resources.getIdentifier("ff_sdl_avatar_margin", "dimen", Constants.SNAPCHAT_PACKAGE_NAME))
        val ffSdlAvatarSize = context.resources.getDimensionPixelSize(context.resources.getIdentifier("ff_sdl_avatar_size", "dimen", Constants.SNAPCHAT_PACKAGE_NAME))
        val ffSdlAvatarStartMargin = context.resources.getDimensionPixelSize(context.resources.getIdentifier("ff_sdl_avatar_start_margin", "dimen", Constants.SNAPCHAT_PACKAGE_NAME))
        val ffSdlPrimaryTextStartMargin = context.resources.getDimensionPixelSize(context.resources.getIdentifier("ff_sdl_primary_text_start_margin", "dimen", Constants.SNAPCHAT_PACKAGE_NAME))

        context.event.subscribe(BindViewEvent::class) { param ->
            param.friendFeedItem { conversationId ->
                val frameLayout = param.view as FrameLayout
                val ffItem = frameLayout.findViewById<View>(ffItemId)

                context.log.verbose("updated $conversationId")

                ffItem.layoutParams = ffItem.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

                frameLayout.removeForegroundDrawable("ffItem")

                val stringMessages = context.database.getMessagesFromConversationId(conversationId, 5)?.mapNotNull { message ->
                    message.messageContent
                        ?.let { ProtoReader(it) }
                        ?.followPath(4, 4)
                        ?.let { messageContainer ->
                        messageContainer.getString(2, 1) ?: ContentType.fromMessageContainer(messageContainer)?.name ?: return@mapNotNull null
                    }
                } ?: return@friendFeedItem

                frameLayout.addForegroundDrawable("ffItem", ShapeDrawable(object: Shape() {
                    override fun draw(canvas: Canvas, paint: Paint) {
                        val offsetY = canvas.height.toFloat() - (stringMessages.size * 30f)

                        stringMessages.forEachIndexed { index, messageString ->
                            paint.textSize = 30f
                            paint.color = sigColorTextPrimary
                            canvas.drawText(messageString,
                                ffSdlAvatarStartMargin.toFloat() + ffSdlAvatarMargin * 2 + ffSdlAvatarSize + ffSdlPrimaryTextStartMargin,
                                offsetY + (index * 30f),
                                paint
                            )
                        }

                        ffItem.layoutParams = ffItem.layoutParams.apply {
                            height = ffSdlAvatarSize + ffSdlAvatarMargin * 2 + ffSdlAvatarStartMargin + (stringMessages.size * 30)
                        }
                    }
                }))
            }
        }
    }
}