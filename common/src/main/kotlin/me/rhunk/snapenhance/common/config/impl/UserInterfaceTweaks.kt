package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.FeatureNotice
import me.rhunk.snapenhance.common.data.MessagingRuleType

class UserInterfaceTweaks : ConfigContainer() {
    class BootstrapOverride : ConfigContainer() {
        companion object {
            val tabs = arrayOf("map", "chat", "camera", "discover", "spotlight")
        }

        val appAppearance = unique("app_appearance", "always_light", "always_dark")
        val homeTab = unique("home_tab", *tabs) { addNotices(FeatureNotice.UNSTABLE) }
    }

    inner class FriendFeedMessagePreview : ConfigContainer(hasGlobalState = true) {
        val amount = integer("amount", defaultValue = 1)
    }

    val friendFeedMenuButtons = multiple(
        "friend_feed_menu_buttons","conversation_info", "mark_snaps_as_seen", "mark_stories_as_seen_locally", *MessagingRuleType.entries.filter { it.showInFriendMenu }.map { it.key }.toTypedArray()
    ).apply {
        set(mutableListOf("conversation_info", MessagingRuleType.STEALTH.key))
    }
    val friendFeedMenuPosition = integer("friend_feed_menu_position", defaultValue = 1)
    val amoledDarkMode = boolean("amoled_dark_mode") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val friendFeedMessagePreview = container("friend_feed_message_preview", FriendFeedMessagePreview()) { requireRestart() }
    val snapPreview = boolean("snap_preview") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val bootstrapOverride = container("bootstrap_override", BootstrapOverride()) { requireRestart() }
    val mapFriendNameTags = boolean("map_friend_nametags") { requireRestart() }
    val preventMessageListAutoScroll = boolean("prevent_message_list_auto_scroll") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val streakExpirationInfo = boolean("streak_expiration_info") { requireRestart() }
    val hideFriendFeedEntry = boolean("hide_friend_feed_entry") { requireRestart() }
    val hideStreakRestore = boolean("hide_streak_restore") { requireRestart() }
    val hideQuickAddFriendFeed = boolean("hide_quick_add_friend_feed") { requireRestart() }
    val hideStorySuggestions = multiple("hide_story_suggestions", "hide_friend_suggestions", "hide_suggested_friend_stories") { requireRestart() }
    val hideUiComponents = multiple("hide_ui_components",
        "hide_voice_record_button",
        "hide_stickers_button",
        "hide_live_location_share_button",
        "hide_chat_call_buttons",
        "hide_profile_call_buttons",
        "hide_unread_chat_hint",
    ) { requireRestart() }
    val operaMediaQuickInfo = boolean("opera_media_quick_info") { requireRestart() }
    val oldBitmojiSelfie = unique("old_bitmoji_selfie", "2d", "3d") { requireCleanCache() }
    val disableSpotlight = boolean("disable_spotlight") { requireRestart() }
    val hideSettingsGear = boolean("hide_settings_gear") { requireRestart() }
    val verticalStoryViewer = boolean("vertical_story_viewer") { requireRestart() }
    val fideliusIndicator = boolean("fidelius_indicator") { requireRestart() }
    val stealthModeIndicator = boolean("stealth_mode_indicator") { requireRestart() }
    val editTextOverride = multiple("edit_text_override", "multi_line_chat_input", "bypass_text_input_limit") {
        requireRestart(); addNotices(FeatureNotice.BAN_RISK, FeatureNotice.INTERNAL_BEHAVIOR)
    }
}
