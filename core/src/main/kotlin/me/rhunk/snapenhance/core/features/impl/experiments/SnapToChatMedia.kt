package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class SnapToChatMedia : Feature("SnapToChatMedia", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.experimental.snapToChatMedia.get()) return

        context.event.subscribe(BuildMessageEvent::class, priority = 100) { event ->
            if (event.message.messageContent!!.contentType != ContentType.SNAP) return@subscribe

            val snapMessageContent = ProtoReader(event.message.messageContent!!.content!!).followPath(11)?.getBuffer() ?: return@subscribe
            event.message.messageContent!!.content = ProtoWriter().apply {
                from(3) {
                    addBuffer(3, snapMessageContent)
                }
            }.toByteArray()
        }
    }
}