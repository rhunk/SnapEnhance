package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.common.ReceiversConfig
import me.rhunk.snapenhance.core.event.events.impl.OnSnapInteractionEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.util.EvictingMap
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.getObjectFieldOrNull
import me.rhunk.snapenhance.core.wrapper.impl.ConversationManager
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.core.wrapper.impl.toSnapUUID

class Messaging : Feature("Messaging", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC or FeatureLoadParams.INIT_ASYNC or FeatureLoadParams.INIT_SYNC) {
    var conversationManager: ConversationManager? = null
        private set
    private var conversationManagerDelegate: Any? = null
    var openedConversationUUID: SnapUUID? = null
        private set
    var lastFetchConversationUserUUID: SnapUUID? = null
        private set
    var lastFetchConversationUUID: SnapUUID? = null
        private set
    var lastFetchGroupConversationUUID: SnapUUID? = null
    var lastFocusedMessageId: Long = -1
        private set

    private val feedCachedSnapMessages = EvictingMap<String, List<Long>>(100)

    override fun init() {
        context.classCache.conversationManager.hookConstructor(HookStage.BEFORE) { param ->
            conversationManager = ConversationManager(context, param.thisObject())
            context.messagingBridge.triggerSessionStart()
            context.mainActivity?.takeIf { it.intent.getBooleanExtra(ReceiversConfig.MESSAGING_PREVIEW_EXTRA,false) }?.run {
                finishAndRemoveTask()
            }
        }
    }

    fun getFeedCachedMessageIds(conversationId: String) = feedCachedSnapMessages[conversationId]

    fun clearConversationFromFeed(conversationId: String, onError : (String) -> Unit = {}, onSuccess : () -> Unit = {}) {
        conversationManager?.clearConversation(conversationId, onError = { onError(it) }, onSuccess = {
            runCatching {
                conversationManagerDelegate!!.let {
                    it::class.java.methods.first { method ->
                        method.name == "onConversationRemoved"
                    }.invoke(conversationManagerDelegate, conversationId.toSnapUUID().instanceNonNull())
                }
                onSuccess()
            }.onFailure {
                context.log.error("Failed to invoke onConversationRemoved: $it")
                onError(it.message ?: "Unknown error")
            }
        })
    }

    fun localUpdateMessage(conversationId: String, message: Message) {
        conversationManagerDelegate?.let {
            it::class.java.methods.first { method ->
                method.name == "onConversationUpdated"
            }.invoke(conversationManagerDelegate, conversationId.toSnapUUID().instanceNonNull(), null, mutableListOf(message.instanceNonNull()), mutableListOf<Any>())
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

        context.mappings.getMappedClass("callbacks", "ConversationManagerDelegate").apply {
            hookConstructor(HookStage.AFTER) { param ->
                conversationManagerDelegate = param.thisObject()
            }
        }

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val interactionInfo = instance.getObjectFieldOrNull("mInteractionInfo") ?: return@hookConstructor
            val messages = (interactionInfo.getObjectFieldOrNull("mMessages") as? List<*>)?.map { Message(it) } ?: return@hookConstructor
            val conversationId = SnapUUID(instance.getObjectFieldOrNull("mConversationId") ?: return@hookConstructor).toString()
            val myUserId = context.database.myUserId

            feedCachedSnapMessages[conversationId] = messages.filter { msg ->
                msg.messageMetadata?.openedBy?.none { it.toString() == myUserId } == true
            }.sortedBy { it.orderKey }.mapNotNull { it.messageDescriptor?.messageId }
        }

        context.mappings.getMappedClass("callbacks", "GetOneOnOneConversationIdsCallback").hook("onSuccess", HookStage.BEFORE) { param ->
            val userIdToConversation = (param.arg<ArrayList<*>>(0))
                .takeIf { it.isNotEmpty() }
                ?.get(0) ?: return@hook

            lastFetchConversationUUID = SnapUUID(userIdToConversation.getObjectField("mConversationId"))
            lastFetchConversationUserUUID = SnapUUID(userIdToConversation.getObjectField("mUserId"))
        }

        with(context.classCache.conversationManager) {
            Hooker.hook(this, "enterConversation", HookStage.BEFORE) { param ->
                openedConversationUUID = SnapUUID(param.arg(0))
                if (context.config.messaging.bypassMessageRetentionPolicy.get()) {
                    val callback = param.argNullable<Any>(2) ?: return@hook
                    callback::class.java.methods.firstOrNull { it.name == "onSuccess" }?.invoke(callback)
                    param.setResult(null)
                }
            }

            Hooker.hook(this, "exitConversation", HookStage.BEFORE) {
                openedConversationUUID = null
            }
        }
    }

    override fun asyncInit() {
        val stealthMode = context.feature(StealthMode::class)

        arrayOf("activate", "deactivate", "processTypingActivity").forEach { hook ->
            Hooker.hook(context.classCache.presenceSession, hook, HookStage.BEFORE, {
                context.config.messaging.hideBitmojiPresence.get() || stealthMode.canUseRule(openedConversationUUID.toString())
            }) {
                it.setResult(null)
            }
        }

        context.classCache.presenceSession.hook("startPeeking", HookStage.BEFORE, {
            context.config.messaging.hidePeekAPeek.get() || stealthMode.canUseRule(openedConversationUUID.toString())
        }) { it.setResult(null) }

        //get last opened snap for media downloader
        context.event.subscribe(OnSnapInteractionEvent::class) { event ->
            openedConversationUUID = event.conversationId
            lastFocusedMessageId = event.messageId
        }

        context.classCache.conversationManager.hook("fetchMessage", HookStage.BEFORE) { param ->
            lastFetchConversationUserUUID = SnapUUID((param.arg(0) as Any))
            lastFocusedMessageId = param.arg(1)
        }

        context.classCache.conversationManager.hook("sendTypingNotification", HookStage.BEFORE, {
            context.config.messaging.hideTypingNotifications.get() || stealthMode.canUseRule(openedConversationUUID.toString())
        }) {
            it.setResult(null)
        }
    }
}