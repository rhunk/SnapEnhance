package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.messaging.MessageSender
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.nativelib.NativeLib

class SendOverride : Feature("Send Override", loadParams = FeatureLoadParams.INIT_SYNC) {
    private var isLastSnapSavable = false
    private val arroyoMessageContainerPath = intArrayOf(4, 4)
    private val typeNames by lazy {
        mutableListOf(
            "ORIGINAL",
            "SNAP",
            "NOTE"
        ).also {
            if (NativeLib.initialized) {
                it.add("SAVABLE_SNAP")
            }
        }.associateWith {
            it
        }
    }

    override fun init() {
        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            val protoEditor = ProtoEditor(event.buffer)

            if (isLastSnapSavable && ProtoReader(event.buffer).containsPath(*arroyoMessageContainerPath, 11)) {
                protoEditor.edit(*arroyoMessageContainerPath, 11, 5, 2) {
                    remove(8)
                    addBuffer(6, byteArrayOf())
                }
                //make snaps savable in chat
                protoEditor.edit(4) {
                    val savableState = firstOrNull(7)?.value ?: return@edit
                    if (savableState == 2L) {
                        remove(7)
                        addVarInt(7, 3)
                    }
                }
            }
            event.buffer = protoEditor.toByteArray()
        }

        context.event.subscribe(SendMessageWithContentEvent::class, {
            context.config.messaging.galleryMediaSendOverride.get()
        }) { event ->
            isLastSnapSavable = false
            if (event.destinations.stories?.isNotEmpty() == true && event.destinations.conversations?.isEmpty() == true) return@subscribe
            val localMessageContent = event.messageContent
            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA) return@subscribe

            //prevent story replies
            val messageProtoReader = ProtoReader(localMessageContent.content!!)
            if (messageProtoReader.contains(7)) return@subscribe

            event.canceled = true

            context.runOnUiThread {
                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                    .setItems(typeNames.values.map {
                        context.translation["features.options.gallery_media_send_override.$it"]
                    }.toTypedArray()) { dialog, which ->
                        dialog.dismiss()
                        val overrideType = typeNames.keys.toTypedArray()[which]

                        if (overrideType != "ORIGINAL" && messageProtoReader.followPath(3)?.getCount(3) != 1) {
                            context.runOnUiThread {
                                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                                    .setMessage(context.translation["gallery_media_send_override.multiple_media_toast"])
                                    .setPositiveButton(context.translation["button.ok"], null)
                                    .show()
                            }
                            return@setItems
                        }

                        when (overrideType) {
                            "SNAP", "SAVABLE_SNAP" -> {
                                val extras = messageProtoReader.followPath(3, 3, 13)?.getBuffer()

                                localMessageContent.contentType = ContentType.SNAP
                                localMessageContent.content = MessageSender.redSnapProto(extras)
                                if (overrideType == "SAVABLE_SNAP") {
                                    isLastSnapSavable = true
                                }
                            }

                            "NOTE" -> {
                                localMessageContent.contentType = ContentType.NOTE
                                val mediaDuration =
                                    messageProtoReader.getVarInt(3, 3, 5, 1, 1, 15) ?: 0
                                localMessageContent.content =
                                    MessageSender.audioNoteProto(mediaDuration)
                            }
                        }

                        event.invokeOriginal()
                    }
                    .setNegativeButton(context.translation["button.cancel"], null)
                    .show()
            }
        }
    }
}