package me.rhunk.snapenhance.features.impl.tweaks

import android.app.AlertDialog
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageSender
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.util.protobuf.ProtoReader

class GalleryMediaSendOverride : Feature("Gallery Media Send Override", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        Hooker.hook(context.classCache.conversationManager, "sendMessageWithContent", HookStage.BEFORE) { param ->
            val overrideType = context.config.state(ConfigProperty.GALLERY_MEDIA_SEND_OVERRIDE).also { if (it == "OFF") return@hook }

            val localMessageContent = MessageContent(param.arg(1))
            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA) return@hook
            //story replies
            val messageProtoReader = ProtoReader(localMessageContent.content)
            if (messageProtoReader.exists(7)) return@hook

            if (messageProtoReader.readPath(3)?.getCount(3) != 1) {
                context.runOnUiThread {
                    ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                        .setMessage("You can only send one media at a time")
                        .setPositiveButton("OK", null)
                        .show()
                }
                param.setResult(null)
                return@hook
            }

            when (overrideType) {
                "SNAP", "LIVE_SNAP" -> {
                    localMessageContent.contentType = ContentType.SNAP
                    localMessageContent.content = MessageSender.redSnapProto(overrideType == "LIVE_SNAP")
                }
                "NOTE" -> {
                    localMessageContent.contentType = ContentType.NOTE
                    val mediaDuration = messageProtoReader.getLong(3, 3, 5, 1, 1, 15) ?: 0
                    localMessageContent.content = MessageSender.audioNoteProto(mediaDuration)
                }
            }
        }
    }
}