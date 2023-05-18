package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import me.rhunk.snapenhance.util.protobuf.ProtoWriter

class ExternalMediaAsSnap : Feature("External Media As Snap", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val redSnapProto: ByteArray by lazy {
        ProtoWriter().apply {
            write(11, 5) {
                write(1) {
                    write(1) {
                        writeConstant(2, 0)
                        writeConstant(12, 0)
                        writeConstant(15, 0)
                    }
                    writeConstant(6, 0)
                }
                write(2) {
                    writeConstant(5, 0)
                    writeBuffer(6, byteArrayOf())
                }
            }
        }.toByteArray()
    }

    override fun init() {
        Hooker.hook(context.classCache.conversationManager, "sendMessageWithContent", HookStage.BEFORE, {
            context.config.bool(ConfigProperty.EXTERNAL_MEDIA_AS_SNAP)
        }) { param ->
            val localMessageContent = MessageContent(param.arg(1))

            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA) return@hook
            //story replies
            if (ProtoReader(localMessageContent.content).exists(7)) return@hook

            localMessageContent.contentType = ContentType.SNAP
            localMessageContent.content = redSnapProto
        }
    }
}