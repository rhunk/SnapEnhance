package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageSender
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.util.protobuf.ProtoReader

class GalleryMediaSendOverride : Feature("Gallery Media Send Override", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val typeNames = listOf(
            "ORIGINAL",
            "SNAP",
            "LIVE_SNAP",
            "NOTE"
        ).associateWith {
           it
        }

        context.event.subscribe(SendMessageWithContentEvent::class, {
            context.config.messaging.galleryMediaSendOverride.get()
        }) { event ->

            val localMessageContent = event.messageContent
            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA) return@subscribe

            //prevent story replies
            val messageProtoReader = ProtoReader(localMessageContent.content)
            if (messageProtoReader.exists(7)) return@subscribe

            event.canceled = true

            context.runOnUiThread {
                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                    .setItems(typeNames.values.toTypedArray()) { dialog, which ->
                        dialog.dismiss()
                        val overrideType = typeNames.keys.toTypedArray()[which]

                        if (overrideType != "ORIGINAL" && messageProtoReader.readPath(3)?.getCount(3) != 1) {
                            context.runOnUiThread {
                                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                                    .setMessage(context.translation["gallery_media_send_override.multiple_media_toast"])
                                    .setPositiveButton(context.translation["button.ok"], null)
                                    .show()
                            }
                            return@setItems
                        }

                        when (overrideType) {
                            "SNAP", "LIVE_SNAP" -> {
                                localMessageContent.contentType = ContentType.SNAP
                                localMessageContent.content = MessageSender.redSnapProto(overrideType == "LIVE_SNAP")
                            }

                            "NOTE" -> {
                                localMessageContent.contentType = ContentType.NOTE
                                val mediaDuration =
                                    messageProtoReader.getLong(3, 3, 5, 1, 1, 15) ?: 0
                                localMessageContent.content =
                                    MessageSender.audioNoteProto(mediaDuration)
                            }
                        }

                        event.adapter.invokeOriginal()
                    }
                    .setNegativeButton(context.translation["button.cancel"], null)
                    .show()
            }
        }
    }
}