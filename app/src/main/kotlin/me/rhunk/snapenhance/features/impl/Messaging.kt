package me.rhunk.snapenhance.features.impl

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class Messaging : Feature("Messaging", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC or FeatureLoadParams.INIT_ASYNC or FeatureLoadParams.INIT_SYNC) {
    lateinit var conversationManager: Any

    var lastOpenedConversationUUID: SnapUUID? = null
    var lastFetchConversationUserUUID: SnapUUID? = null
    var lastFetchConversationUUID: SnapUUID? = null
    var lastFocusedMessageId: Long = -1

    override fun init() {
        Hooker.hookConstructor(context.classCache.conversationManager, HookStage.BEFORE) {
            conversationManager = it.thisObject()
        }
    }

    override fun onActivityCreate() {
        with(context.classCache.conversationManager) {
            Hooker.hook(this, "enterConversation", HookStage.BEFORE) {
                lastOpenedConversationUUID = SnapUUID(it.arg(0))
            }

            Hooker.hook(this, "getOneOnOneConversationIds", HookStage.BEFORE) { param ->
                val conversationIds: List<Any> = param.arg(0)
                if (conversationIds.isNotEmpty()) {
                    lastFetchConversationUserUUID = SnapUUID(conversationIds[0])
                }
            }

            Hooker.hook(this, "exitConversation", HookStage.BEFORE) {
                lastOpenedConversationUUID = null
            }

            Hooker.hook(this, "fetchConversation", HookStage.BEFORE) {
                lastFetchConversationUUID = SnapUUID(it.arg(0))
            }
        }

    }

    override fun asyncInit() {
        arrayOf("activate", "deactivate", "processTypingActivity").forEach { hook ->
            Hooker.hook(context.classCache.presenceSession, hook, HookStage.BEFORE, { context.config.bool(ConfigProperty.HIDE_BITMOJI_PRESENCE) }) {
                it.setResult(null)
            }
        }

        //get last opened snap for media downloader
        Hooker.hook(context.classCache.snapManager, "onSnapInteraction", HookStage.BEFORE) { param ->
            lastOpenedConversationUUID = SnapUUID(param.arg(1))
            lastFocusedMessageId = param.arg(2)
        }

        Hooker.hook(context.classCache.conversationManager, "fetchMessage", HookStage.BEFORE) { param ->
            lastFetchConversationUserUUID = SnapUUID((param.arg(0) as Any))
            lastFocusedMessageId = param.arg(1)
        }

        Hooker.hook(context.classCache.conversationManager, "sendTypingNotification", HookStage.BEFORE,
            {context.config.bool(ConfigProperty.HIDE_TYPING_NOTIFICATION)}) {
            it.setResult(null)
        }
    }
}