package me.rhunk.snapenhance.features.impl

import me.rhunk.snapenhance.core.eventbus.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.ktx.getObjectField

class Messaging : Feature("Messaging", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC or FeatureLoadParams.INIT_ASYNC or FeatureLoadParams.INIT_SYNC) {
    lateinit var conversationManager: Any

    var openedConversationUUID: SnapUUID? = null
    var lastFetchConversationUserUUID: SnapUUID? = null
    var lastFetchConversationUUID: SnapUUID? = null
    var lastFetchGroupConversationUUID: SnapUUID? = null
    var lastFocusedMessageId: Long = -1

    override fun init() {
        Hooker.hookConstructor(context.classCache.conversationManager, HookStage.BEFORE) {
            conversationManager = it.thisObject()
        }
    }

    override fun onActivityCreate() {
        context.mappings.getMappedObjectNullable("FriendsFeedEventDispatcher").let { it as? Map<*, *> }?.let { mappings ->
            findClass(mappings["class"].toString()).hook("onItemLongPress", HookStage.BEFORE) { param ->
                val viewItemContainer = param.arg<Any>(0)
                val viewItem = viewItemContainer.getObjectField(mappings["viewModelField"].toString()).toString()
                val conversationId = viewItem.substringAfter("conversationId: ").substring(0, 36).also {
                    if (it.startsWith("null")) return@hook
                }
                context.database.getConversationType(conversationId)?.takeIf { it == 1 }?.run {
                    lastFetchGroupConversationUUID = SnapUUID.fromString(conversationId)
                }
            }
        }

        context.mappings.getMappedClass("callbacks", "GetOneOnOneConversationIdsCallback").hook("onSuccess", HookStage.BEFORE) { param ->
            val userIdToConversation = (param.arg<ArrayList<*>>(0))
                .takeIf { it.isNotEmpty() }
                ?.get(0) ?: return@hook

            lastFetchConversationUUID = SnapUUID(userIdToConversation.getObjectField("mConversationId"))
            lastFetchConversationUserUUID = SnapUUID(userIdToConversation.getObjectField("mUserId"))
        }

        with(context.classCache.conversationManager) {
            Hooker.hook(this, "enterConversation", HookStage.BEFORE) {
                openedConversationUUID = SnapUUID(it.arg(0))
            }

            Hooker.hook(this, "exitConversation", HookStage.BEFORE) {
                openedConversationUUID = null
            }
        }

    }

    override fun asyncInit() {
        val stealthMode = context.feature(StealthMode::class)

        val hideBitmojiPresence by context.config.messaging.hideBitmojiPresence
        val hideTypingNotification by context.config.messaging.hideTypingNotifications

        arrayOf("activate", "deactivate", "processTypingActivity").forEach { hook ->
            Hooker.hook(context.classCache.presenceSession, hook, HookStage.BEFORE, {
                hideBitmojiPresence || stealthMode.canUseRule(openedConversationUUID.toString())
            }) {
                it.setResult(null)
            }
        }

        //get last opened snap for media downloader
        context.event.subscribe(OnSnapInteractionEvent::class) { event ->
            openedConversationUUID = event.conversationId
            lastFocusedMessageId = event.messageId
        }

        Hooker.hook(context.classCache.conversationManager, "fetchMessage", HookStage.BEFORE) { param ->
            lastFetchConversationUserUUID = SnapUUID((param.arg(0) as Any))
            lastFocusedMessageId = param.arg(1)
        }

        Hooker.hook(context.classCache.conversationManager, "sendTypingNotification", HookStage.BEFORE, {
            hideTypingNotification || stealthMode.canUseRule(openedConversationUUID.toString())
        }) {
            it.setResult(null)
        }
    }
}