package me.rhunk.snapenhance.core.features.impl.ui

import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.RuleState
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.mapper.impl.CallbackMapper

class HideFriendFeedEntry : MessagingRuleFeature("HideFriendFeedEntry", ruleType = MessagingRuleType.HIDE_FRIEND_FEED, loadParams = FeatureLoadParams.INIT_SYNC) {
    private fun createDeletedFeedEntry(conversationId: String) = context.gson.fromJson(
        """
            {
                "mFeedEntryIdentifier": {
                    "mConversationId": null
                },
                "mReason": "CLEAR_CONVERSATION"
            }
        """.trimIndent(),
        findClass("com.snapchat.client.messaging.DeletedFeedEntry")
    ).also {
        it.getObjectField("mFeedEntryIdentifier")?.setObjectField("mConversationId", SnapUUID.fromString(conversationId).instanceNonNull())
    }

    private fun filterFriendFeed(entries: ArrayList<Any>, deletedEntries: ArrayList<Any>? = null) {
        entries.removeIf { feedEntry ->
            val conversationId = SnapUUID(feedEntry.getObjectField("mConversationId")).toString()

            if (canUseRule(conversationId)) {
                deletedEntries?.add(createDeletedFeedEntry(conversationId))
                true
            } else {
                false
            }
        }
    }

    override fun init() {
        if (!context.config.userInterface.hideFriendFeedEntry.get()) return

        context.mappings.useMapper(CallbackMapper::class) {
            arrayOf(
                "QueryFeedCallback" to "onQueryFeedComplete",
                "FeedManagerDelegate" to "onFeedEntriesUpdated",
                "FeedManagerDelegate" to "onInternalSyncFeed",
                "SyncFeedCallback" to "onSyncFeedComplete",
            ).forEach { (callbackName, methodName) ->
                findClass(callbacks.get()!![callbackName] ?: return@forEach).hook(methodName, HookStage.BEFORE) { param ->
                    filterFriendFeed(param.arg(0))
                }
            }

            callbacks.getClass("FetchAndSyncFeedCallback")
                ?.hook("onFetchAndSyncFeedComplete", HookStage.BEFORE) { param ->
                    val deletedConversations: ArrayList<Any> = param.arg(2)
                    filterFriendFeed(param.arg(0), deletedConversations)

                    if (deletedConversations.any {
                            val uuid = SnapUUID(it.getObjectField("mFeedEntryIdentifier")?.getObjectField("mConversationId")).toString()
                            context.database.getFeedEntryByConversationId(uuid) != null
                        }) {
                        param.setArg(4, true)
                    }
                }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}