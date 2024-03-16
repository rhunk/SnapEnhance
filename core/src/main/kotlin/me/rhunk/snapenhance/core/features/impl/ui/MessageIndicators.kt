package me.rhunk.snapenhance.core.features.impl.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.AppleLogo
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable
import kotlin.random.Random

class MessageIndicators : Feature("Message Indicators", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        val messageIndicatorsConfig = context.config.userInterface.messageIndicators.getNullable() ?: return
        if (messageIndicatorsConfig.isEmpty()) return

        val messageInfoTag = Random.nextLong().toString()
        val appleLogo = AppleLogo

        context.event.subscribe(BindViewEvent::class) { event ->
            event.chatMessage { _, messageId ->
                val parentLinearLayout = event.view.parent as? ViewGroup ?: return@subscribe
                parentLinearLayout.findViewWithTag<View>(messageInfoTag)?.let { parentLinearLayout.removeView(it) }

                event.view.removeForegroundDrawable("messageIndicators")

                val message = context.database.getConversationMessageFromId(messageId.toLong()) ?: return@chatMessage
                if (message.contentType != ContentType.SNAP.id && message.contentType != ContentType.EXTERNAL_MEDIA.id) return@chatMessage
                val reader = ProtoReader(message.messageContent ?: return@chatMessage)

                val hasEncryption = if (reader.containsPath(4, 3)) reader.getByteArray(4, 3, 1) == null else false
                val sentFromIosDevice = if (reader.containsPath(4, 4, 3)) !reader.containsPath(4, 4, 3, 3, 17) else reader.getVarInt(4, 4, 11, 17, 7) != null
                val sentFromWebApp = reader.getVarInt(4, 4, *(if (reader.containsPath(4, 4, 3)) intArrayOf(3, 3, 22, 1) else intArrayOf(11, 22, 1))) == 7L
                val sentWithLocation = reader.getVarInt(4, 4, 11, 17, 5) != null

                createComposeView(event.view.context) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 1.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Row {
                            if (messageIndicatorsConfig.contains("location_indicator")) {
                                if (sentWithLocation) {
                                    Image(
                                        imageVector = Icons.Default.LocationOn,
                                        colorFilter = ColorFilter.tint(Color.Green),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            if (messageIndicatorsConfig.contains("platform_indicator")) {
                                Image(
                                    imageVector = when {
                                        sentFromWebApp -> Icons.Default.Laptop
                                        sentFromIosDevice -> appleLogo
                                        else -> Icons.Default.Android
                                    },
                                    colorFilter = ColorFilter.tint(Color.Green),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            if (hasEncryption && messageIndicatorsConfig.contains("encryption_indicator")) {
                                Image(
                                    imageVector = Icons.Default.Lock,
                                    colorFilter = ColorFilter.tint(Color.Green),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }.apply {
                    tag = messageInfoTag
                    addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                        layout(left, 0, right, 0)
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    parentLinearLayout.addView(this)
                }
            }
        }
    }
}