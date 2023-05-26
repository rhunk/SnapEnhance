package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.protobuf.ProtoEditor

class UnlimitedSnapViewTime :
    Feature("UnlimitedSnapViewTime", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, {
            context.config.bool(ConfigProperty.UNLIMITED_SNAP_VIEW_TIME)
        }) { param ->
            val message = Message(param.thisObject())
            if (message.messageState != MessageState.COMMITTED) return@hookConstructor
            if (message.messageContent.contentType != ContentType.SNAP) return@hookConstructor

            message.messageContent.content = ProtoEditor(message.messageContent.content).apply {
                edit(11, 5, 2) {
                    writeConstant(5, 0)
                    writeBuffer(6, byteArrayOf())
                }
            }.toByteArray()
        }
    }
}
