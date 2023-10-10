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
        "friend_feed_menu_buttons","conversation_info", *MessagingRuleType.entries.filter { it.showInFriendMenu }.map { it.key }.toTypedArray()
    ).apply {
        set(mutableListOf("conversation_info", MessagingRuleType.STEALTH.key))
    }
    val friendFeedMenuPosition = integer("friend_feed_menu_position", defaultValue = 1)
    val amoledDarkMode = boolean("amoled_dark_mode") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val friendFeedMessagePreview = container("friend_feed_message_preview", FriendFeedMessagePreview()) { requireRestart() }
    val bootstrapOverride = container("bootstrap_override", BootstrapOverride()) { requireRestart() }
    val mapFriendNameTags = boolean("map_friend_nametags") { requireRestart() }
    val streakExpirationInfo = boolean("streak_expiration_info") { requireRestart() }
    val hideStreakRestore = boolean("hide_streak_restore") { requireRestart() }
    val hideStorySections = multiple("hide_story_sections",
        "hide_friend_suggestions", "hide_friends", "hide_suggested", "hide_for_you") { requireRestart() }
    val hideUiComponents = multiple("hide_ui_components",
        "hide_voice_record_button",
        "hide_stickers_button",
        "hide_live_location_share_button",
        "hide_chat_call_buttons",
        "hide_profile_call_buttons"
    ) { requireRestart() }
    val ddBitmojiSelfie = boolean("2d_bitmoji_selfie") { requireCleanCache() }
    val disableSpotlight = boolean("disable_spotlight") { requireRestart() }
    val storyViewerOverride = unique("story_viewer_override", "DISCOVER_PLAYBACK_SEEKBAR", "VERTICAL_STORY_VIEWER") { requireRestart() }
}
