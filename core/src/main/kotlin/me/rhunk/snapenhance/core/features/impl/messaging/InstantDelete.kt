package me.rhunk.snapenhance.core.features.impl.messaging

import android.view.View
import android.widget.TextView
import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.iterateParent
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.util.ktx.getIdentifier
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.impl.CallbackResult
import java.lang.reflect.Modifier

class InstantDelete : Feature("InstantDelete", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.messaging.instantDelete.get()) return
        val chatActionMenuOptions = listOf(
            "chat_action_menu_erase_messages",
            "chat_action_menu_erase_quote",
            "chat_action_menu_erase_reply",
        ).associateWith { context.resources.getString(context.resources.getIdentifier(it, "string")) }

        val chatActionMenuContainerID = context.resources.getId("chat_action_menu_container")
        val actionMenuOptionId = context.resources.getId("action_menu_option")
        val actionMenuOptionTextId = context.resources.getId("action_menu_option_text")

        context.event.subscribe(BindViewEvent::class) { event ->
            if (event.view.id != actionMenuOptionId) return@subscribe

            val menuOptionText = event.view.findViewById<TextView>(actionMenuOptionTextId) ?: return@subscribe
            if (!chatActionMenuOptions.values.contains(menuOptionText.text)) return@subscribe

            val viewModel = event.prevModel

            val nestedViewOnClickListenerField = viewModel::class.java.fields.find {
                it.type == View.OnClickListener::class.java
            } ?: return@subscribe

            val nestedViewOnClickListener = nestedViewOnClickListenerField.get(viewModel) as? View.OnClickListener ?: return@subscribe

            val chatViewModel = nestedViewOnClickListener::class.java.fields.find {
                Modifier.isAbstract(it.type.modifiers) && runCatching {
                    it.get(nestedViewOnClickListener)
                }.getOrNull().toString().startsWith("ChatViewModel")
            }?.get(nestedViewOnClickListener) ?: return@subscribe

            //[convId]:arroyo-id:[messageId]
            val (conversationId, messageId) = chatViewModel.toString().substringAfter("messageId=").substringBefore(",").split(":").let {
                if (it.size != 3) return@let null
                it[0] to it[2]
            } ?: return@subscribe

            viewModel.setObjectField(nestedViewOnClickListenerField.name, View.OnClickListener { view ->
                val onCallbackResult: CallbackResult = callbackResult@{
                    if (it == null || it == "DUPLICATEREQUEST") return@callbackResult
                    context.log.error("Error deleting message $messageId: $it")
                    context.shortToast("Error deleting message $messageId: $it. Using fallback method")
                    context.runOnUiThread {
                        nestedViewOnClickListener.onClick(view)
                    }
                }

                runCatching {
                    val conversationManager = context.feature(Messaging::class).conversationManager ?: return@runCatching

                    if (chatActionMenuOptions["chat_action_menu_erase_quote"] == menuOptionText.text) {
                        conversationManager.fetchMessage(conversationId, messageId.toLong(), onSuccess = { message ->
                            val quotedMessage = message.messageContent!!.quotedMessage!!.takeIf { it.isPresent() }!!

                            conversationManager.updateMessage(
                                conversationId,
                                quotedMessage.content!!.messageId!!,
                                MessageUpdate.ERASE,
                                onResult = onCallbackResult
                            )
                        }, onError = {
                            onCallbackResult(it)
                        })
                        return@runCatching
                    }

                    conversationManager.updateMessage(
                        conversationId,
                        messageId.toLong(),
                        MessageUpdate.ERASE,
                        onResult = onCallbackResult
                    )
                }.onFailure {
                    context.log.error("Error deleting message $messageId", it)
                    onCallbackResult(it.message)
                    return@OnClickListener
                }

                view.iterateParent {
                    if (it.id != chatActionMenuContainerID) return@iterateParent false
                    it.triggerCloseTouchEvent()
                    true
                }
            })
        }
    }
}