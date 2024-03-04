package me.rhunk.snapenhance.common.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


data class FriendPresenceState(
    val bitmojiPresent: Boolean,
    val typing: Boolean,
    val wasTyping: Boolean,
    val speaking: Boolean,
    val peeking: Boolean
)

open class SessionEvent(
    val type: SessionEventType,
    val conversationId: String,
    val authorUserId: String,
)

class SessionMessageEvent(
    type: SessionEventType,
    conversationId: String,
    authorUserId: String,
    val serverMessageId: Long,
    val messageData: ByteArray? = null,
    val reactionId: Int? = null,
) : SessionEvent(type, conversationId, authorUserId)


enum class SessionEventType(
    val key: String
) {
    MESSAGE_READ_RECEIPTS("message_read_receipts"),
    MESSAGE_DELETED("message_deleted"),
    MESSAGE_SAVED("message_saved"),
    MESSAGE_UNSAVED("message_unsaved"),
    MESSAGE_REACTION_ADD("message_reaction_add"),
    MESSAGE_REACTION_REMOVE("message_reaction_remove"),
    SNAP_OPENED("snap_opened"),
    SNAP_REPLAYED("snap_replayed"),
    SNAP_REPLAYED_TWICE("snap_replayed_twice"),
    SNAP_SCREENSHOT("snap_screenshot"),
    SNAP_SCREEN_RECORD("snap_screen_record"),
}

enum class TrackerEventType(
    val key: String
) {
    // pcs events
    CONVERSATION_ENTER("conversation_enter"),
    CONVERSATION_EXIT("conversation_exit"),
    STARTED_TYPING("started_typing"),
    STOPPED_TYPING("stopped_typing"),
    STARTED_SPEAKING("started_speaking"),
    STOPPED_SPEAKING("stopped_speaking"),
    STARTED_PEEKING("started_peeking"),
    STOPPED_PEEKING("stopped_peeking"),

    // mcs events
    MESSAGE_READ("message_read"),
    MESSAGE_DELETED("message_deleted"),
    MESSAGE_SAVED("message_saved"),
    MESSAGE_UNSAVED("message_unsaved"),
    MESSAGE_REACTION_ADD("message_reaction_add"),
    MESSAGE_REACTION_REMOVE("message_reaction_remove"),
    SNAP_OPENED("snap_opened"),
    SNAP_REPLAYED("snap_replayed"),
    SNAP_REPLAYED_TWICE("snap_replayed_twice"),
    SNAP_SCREENSHOT("snap_screenshot"),
    SNAP_SCREEN_RECORD("snap_screen_record"),
}


@Parcelize
class TrackerEventsResult(
    val rules: Map<TrackerRule, List<TrackerRuleEvent>>
): Parcelable {
    fun getActions(): Map<TrackerRuleAction, TrackerRuleActionParams> {
        return rules.flatMap {
            it.value
        }.fold(mutableMapOf()) { acc, ruleEvent ->
            ruleEvent.actions.forEach { action ->
                acc[action] = acc[action]?.merge(ruleEvent.params) ?: ruleEvent.params
            }
            acc
        }
    }

    fun canTrackOn(conversationId: String?, userId: String?): Boolean {
        return rules.any t@{ (rule, ruleEvents) ->
            ruleEvents.any { event ->
                if (!event.enabled) {
                    return@any false
                }

                // global rule
                if (rule.conversationId == null && rule.userId == null) {
                    return@any true
                }

                // user rule
                if (rule.conversationId == null && rule.userId == userId) {
                    return@any true
                }

                // conversation rule
                if (rule.conversationId == conversationId && rule.userId == null) {
                    return@any true
                }

                // conversation and user rule
                return@any rule.conversationId == conversationId && rule.userId == userId
            }
        }
    }
}

enum class TrackerRuleAction {
    LOG,
    IN_APP_NOTIFICATION,
    PUSH_NOTIFICATION,
    CUSTOM,
}

@Parcelize
data class TrackerRuleActionParams(
    var onlyInsideConversation: Boolean = false,
    var onlyOutsideConversation: Boolean = false,
    var onlyWhenAppActive: Boolean = false,
    var onlyWhenAppInactive: Boolean = false,
    var noPushNotificationWhenAppActive: Boolean = false,
): Parcelable {
    fun merge(other: TrackerRuleActionParams): TrackerRuleActionParams {
        return TrackerRuleActionParams(
            onlyInsideConversation = onlyInsideConversation || other.onlyInsideConversation,
            onlyOutsideConversation = onlyOutsideConversation || other.onlyOutsideConversation,
            onlyWhenAppActive = onlyWhenAppActive || other.onlyWhenAppActive,
            onlyWhenAppInactive = onlyWhenAppInactive || other.onlyWhenAppInactive,
            noPushNotificationWhenAppActive = noPushNotificationWhenAppActive || other.noPushNotificationWhenAppActive,
        )
    }
}

@Parcelize
data class TrackerRule(
    val id: Int,
    val conversationId: String?,
    val userId: String?,
): Parcelable

@Parcelize
data class TrackerRuleEvent(
    val id: Int,
    val enabled: Boolean,
    val eventType: String,
    val params: TrackerRuleActionParams,
    val actions: List<TrackerRuleAction>
): Parcelable
