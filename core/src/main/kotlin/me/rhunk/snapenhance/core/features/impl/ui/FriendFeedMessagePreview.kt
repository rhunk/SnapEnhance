package me.rhunk.snapenhance.core.features.impl.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.experiments.EndToEndEncryption
import me.rhunk.snapenhance.core.ui.addForegroundDrawable
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable
import me.rhunk.snapenhance.core.util.EvictingMap
import me.rhunk.snapenhance.core.util.ktx.getDimens
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.util.ktx.getIdentifier
import java.util.WeakHashMap
import kotlin.math.absoluteValue

class FriendFeedMessagePreview : Feature("FriendFeedMessagePreview", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val endToEndEncryption by lazy { context.feature(EndToEndEncryption::class) }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val setting get() = context.config.userInterface.friendFeedMessagePreview
    private val hasE2EE get() = context.config.experimental.e2eEncryption.globalState == true

    private val sigColorTextPrimary by lazy {
        context.mainActivity!!.theme.obtainStyledAttributes(
            intArrayOf(context.resources.getIdentifier("sigColorTextPrimary", "attr"))
        ).getColor(0, 0)
    }

    private val cachedLayouts = WeakHashMap<String, View>()
    private val messageCache = EvictingMap<String, List<String>>(100)
    private val friendNameCache = EvictingMap<String, String>(100)

    private suspend fun fetchMessages(conversationId: String, callback: suspend () -> Unit) {
        val messages = context.database.getMessagesFromConversationId(conversationId, setting.amount.get().absoluteValue)?.mapNotNull { message ->
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
        }?.takeIf { it.isNotEmpty() }?.reversed()

        withContext(Dispatchers.Main) {
            messages?.also { messageCache[conversationId] = it } ?: run {
                messageCache.remove(conversationId)
            }
            callback()
        }
    }

    override fun onActivityCreate() {
        if (setting.globalState != true) return

        val ffItemId = context.resources.getId("ff_item")

        val secondaryTextSize = context.resources.getDimens("ff_feed_cell_secondary_text_size").toFloat()
        val ffSdlAvatarMargin = context.resources.getDimens("ff_sdl_avatar_margin")
        val ffSdlAvatarSize = context.resources.getDimens("ff_sdl_avatar_size")
        val ffSdlAvatarStartMargin = context.resources.getDimens("ff_sdl_avatar_start_margin")
        val ffSdlPrimaryTextStartMargin = context.resources.getDimens("ff_sdl_primary_text_start_margin").toFloat()

        val feedEntryHeight = ffSdlAvatarSize + ffSdlAvatarMargin * 2 + ffSdlAvatarStartMargin
        val separatorHeight = (context.resources.displayMetrics.density * 2).toInt()
        val textPaint = TextPaint().apply {
            textSize = secondaryTextSize
        }

        context.event.subscribe(BuildMessageEvent::class) { param ->
            val conversationId = param.message.messageDescriptor?.conversationId?.toString() ?: return@subscribe
            val cachedView = cachedLayouts[conversationId] ?: return@subscribe
            context.coroutineScope.launch {
                fetchMessages(conversationId) {
                    cachedView.postInvalidateDelayed(100L)
                }
            }
        }

        context.event.subscribe(BindViewEvent::class) { param ->
            param.friendFeedItem { conversationId ->
                val frameLayout = param.view as ViewGroup
                val ffItem = frameLayout.findViewById<View>(ffItemId)

                context.coroutineScope.launch(coroutineDispatcher) {
                    withContext(Dispatchers.Main) {
                        cachedLayouts.remove(conversationId)
                        frameLayout.removeForegroundDrawable("ffItem")
                    }

                    fetchMessages(conversationId) {
                        var maxTextHeight = 0
                        val previewContainerHeight = messageCache[conversationId]?.sumOf { msg ->
                            val rect = Rect()
                            textPaint.getTextBounds(msg, 0, msg.length, rect)
                            rect.height().also {
                                if (it > maxTextHeight) maxTextHeight = it
                            }.plus(separatorHeight)
                        } ?: run {
                            ffItem.layoutParams = ffItem.layoutParams.apply {
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                            }
                            return@fetchMessages
                        }

                        ffItem.layoutParams = ffItem.layoutParams.apply {
                            height = feedEntryHeight + previewContainerHeight + separatorHeight
                        }

                        cachedLayouts[conversationId] = frameLayout

                        frameLayout.addForegroundDrawable("ffItem", ShapeDrawable(object: Shape() {
                            override fun draw(canvas: Canvas, paint: Paint) {
                                val offsetY = canvas.height.toFloat() - previewContainerHeight

                                messageCache[conversationId]?.forEachIndexed { index, messageString ->
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
    }
}