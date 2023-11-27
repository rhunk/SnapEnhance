package me.rhunk.snapenhance.core.features.impl.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.addForegroundDrawable
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable

class FideliusIndicator : Feature("Fidelius Indicator", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.userInterface.fideliusIndicator.get()) return

        context.event.subscribe(BindViewEvent::class) { event ->
            event.chatMessage { _, messageId ->
                event.view.removeForegroundDrawable("fideliusIndicator")

                val message = context.database.getConversationMessageFromId(messageId.toLong()) ?: return@chatMessage
                if (message.senderId == context.database.myUserId) return@chatMessage
                if (message.contentType != ContentType.SNAP.id && message.contentType != ContentType.EXTERNAL_MEDIA.id) return@chatMessage

                if (!ProtoReader(message.messageContent ?: return@chatMessage).containsPath(4, 3, 3, 6)) return@chatMessage

                event.view.addForegroundDrawable("fideliusIndicator", ShapeDrawable(object: Shape() {
                    override fun draw(canvas: Canvas, paint: Paint) {
                        val margin = 25f
                        val radius = 15f

                        canvas.drawCircle(margin + radius, canvas.height - margin - radius, radius, paint.apply {
                            color = 0xFF00FF00.toInt()
                        })
                    }
                }))
            }
        }
    }
}