package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.features.impl.ui.ClientBootstrapOverride

class UserInterfaceTweaks : ConfigContainer() {
    inner class BootstrapOverride : ConfigContainer() {
        val appAppearance = unique("app_appearance", "always_light", "always_dark")
        val homeTab = unique("home_tab", *ClientBootstrapOverride.tabs) { addNotices(FeatureNotice.UNSTABLE)  }
    }

    val friendFeedMenuButtons = multiple(
        "friend_feed_menu_buttons","conversation_info", *MessagingRuleType.values().toList().filter { it.showInFriendMenu }.map { it.key }.toTypedArray()
    ).apply {
        set(mutableListOf("conversation_info", MessagingRuleType.STEALTH.key))
    }
    val friendFeedMenuPosition = integer("friend_feed_menu_position", defaultValue = 1)
    val amoledDarkMode = boolean("amoled_dark_mode") { addNotices(FeatureNotice.UNSTABLE) }
    val bootstrapOverride = container("bootstrap_override", BootstrapOverride())
    val mapFriendNameTags = boolean("map_friend_nametags")
    val streakExpirationInfo = boolean("streak_expiration_info")
    val hideStorySections = multiple("hide_story_sections",
        "hide_friend_suggestions", "hide_friends", "hide_suggested", "hide_for_you")
    val hideUiComponents = multiple("hide_ui_components",
        "hide_voice_record_button",
        "hide_stickers_button",
        "hide_live_location_share_button",
        "hide_chat_call_buttons",
        "hide_profile_call_buttons"
    )
    val ddBitmojiSelfie = boolean("2d_bitmoji_selfie")
    val disableSpotlight = boolean("disable_spotlight")
    val storyViewerOverride = unique("story_viewer_override", "DISCOVER_PLAYBACK_SEEKBAR", "VERTICAL_STORY_VIEWER")
}
