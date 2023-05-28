package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import me.rhunk.snapenhance.util.protobuf.ProtoWriter

class GalleryMediaSendOverride : Feature("Gallery Media Send Override", loadParams = FeatureLoadParams.INIT_SYNC) {
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

    private val audioNoteProto: (Int) -> ByteArray = { duration ->
        ProtoWriter().apply {
            write(6, 1) {
                write(1) {
                    writeConstant(2, 4)
                    write(5) {
                        writeConstant(1, 0)
                        writeConstant(2, 0)
                    }
                    writeConstant(7, 0)
                    writeConstant(13, duration)
                }
            }
        }.toByteArray()
    }

    override fun init() {
        Hooker.hook(context.classCache.conversationManager, "sendMessageWithContent", HookStage.BEFORE) { param ->
            val localMessageContent = MessageContent(param.arg(1))

            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA) return@hook
            //story replies
            val messageProtoReader = ProtoReader(localMessageContent.content)
            if (messageProtoReader.exists(7)) return@hook

            when (context.config.state(ConfigProperty.GALLERY_MEDIA_SEND_OVERRIDE)) {
                "SNAP" -> {
                    localMessageContent.contentType = ContentType.SNAP
                    localMessageContent.content = redSnapProto
                }
                "NOTE" -> {
                    localMessageContent.contentType = ContentType.NOTE
                    val mediaDuration = messageProtoReader.getInt(3, 3, 5, 1, 1, 15) ?: 0
                    localMessageContent.content = audioNoteProto(mediaDuration)
                }
            }
        }
    }
}