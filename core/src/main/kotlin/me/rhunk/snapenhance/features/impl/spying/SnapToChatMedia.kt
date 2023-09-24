package me.rhunk.snapenhance.features.impl.spying

import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor

class SnapToChatMedia : Feature("SnapToChatMedia", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.messaging.snapToChatMedia.get()) return
        context.classCache.message.hookConstructor(HookStage.AFTER) { param ->
            val message = Message(param.thisObject())

            if (message.messageContent.contentType != ContentType.SNAP) return@hookConstructor

            val snapMessageContent = ProtoReader(message.messageContent.content).followPath(11)?.getBuffer() ?: return@hookConstructor
            message.messageContent.content = ProtoWriter().apply {
                from(3) {
                    addBuffer(3, snapMessageContent)
                }
            }.toByteArray()
        }
    }
}