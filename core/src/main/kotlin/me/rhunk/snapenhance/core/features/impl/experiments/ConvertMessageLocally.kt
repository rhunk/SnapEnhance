package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.MessageContent

class ConvertMessageLocally : Feature("Convert Message Edit", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val messageCache = mutableMapOf<Long, MessageContent>()

    private fun dispatchMessageEdit(message: Message, restore: Boolean = false) {
        val messageId = message.messageDescriptor!!.messageId!!
        if (!restore) messageCache[messageId] = message.messageContent!!

        context.runOnUiThread {
            context.feature(Messaging::class).localUpdateMessage(
                message.messageDescriptor!!.conversationId!!.toString(),
                message
            )
        }
    }

    fun convertMessageInterface(messageInstance: Message) {
        val actions = mutableMapOf<String, (Message) -> Unit>()
        actions["restore_original"] = {
            messageCache.remove(it.messageDescriptor!!.messageId!!)
            dispatchMessageEdit(it, restore = true)
        }

        val contentType = messageInstance.messageContent?.contentType
        if (contentType == ContentType.SNAP) {
            actions["convert_external_media"] = convert@{ message ->
                val snapMessageContent = ProtoReader(message.messageContent!!.content!!).followPath(11)
                    ?.getBuffer() ?: return@convert
                message.messageContent!!.content = ProtoWriter().apply {
                    from(3) {
                        addBuffer(3, snapMessageContent)
                    }
                }.toByteArray()
                dispatchMessageEdit(message)
            }
        }

        ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity).apply {
            setItems(actions.keys.toTypedArray()) { _, which ->
                actions.values.elementAt(which).invoke(messageInstance)
            }
            setPositiveButton(this@ConvertMessageLocally.context.translation["button.cancel"]) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    override fun onActivityCreate() {
        context.event.subscribe(BuildMessageEvent::class, priority = 2) {
            val clientMessageId = it.message.messageDescriptor?.messageId ?: return@subscribe
            if (!messageCache.containsKey(clientMessageId)) return@subscribe
            it.message.messageContent = messageCache[clientMessageId]
        }
    }
}