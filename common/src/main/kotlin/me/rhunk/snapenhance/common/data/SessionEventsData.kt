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

object TrackerFlags {
    const val TRACK = 1
    const val LOG = 2
    const val NOTIFY = 4
    const val APP_IS_ACTIVE = 8
    const val APP_IS_INACTIVE = 16
    const val IS_IN_CONVERSATION = 32
}

@Parcelize
class TrackerEventsResult(
    private val rules: Map<TrackerRule, List<TrackerRuleEvent>>
): Parcelable {
    fun hasFlags(vararg flags: Int): Boolean {
        return rules.any { (_, ruleEvents) ->
            ruleEvents.any { flags.all { flag -> it.flags and flag != 0 } }
        }
    }

    fun canTrackOn(conversationId: String?, userId: String?): Boolean {
        return rules.any t@{ (rule, ruleEvents) ->
            ruleEvents.any { event ->
                if (event.flags and TrackerFlags.TRACK == 0) {
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


@Parcelize
data class TrackerRule(
    val id: Int,
    val flags: Int,
    val conversationId: String?,
    val userId: String?
): Parcelable

@Parcelize
data class TrackerRuleEvent(
    val id: Int,
    val flags: Int,
    val eventType: String,
): Parcelable

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
