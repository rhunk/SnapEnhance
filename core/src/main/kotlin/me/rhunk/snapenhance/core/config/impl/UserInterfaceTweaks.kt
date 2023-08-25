package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.core.messaging.MessagingRuleType

class UserInterfaceTweaks : ConfigContainer() {
    val enableAppAppearance = boolean("enable_app_appearance")
    val friendFeedMenuButtons = multiple(
        "friend_feed_menu_buttons","conversation_info", *MessagingRuleType.values().toList().filter { it.listMode }.map { it.key }.toTypedArray()
    ).apply {
        set(mutableListOf("conversation_info", MessagingRuleType.STEALTH.key))
    }
    val friendFeedMenuPosition = integer("friend_feed_menu_position", defaultValue = 1)
    val amoledDarkMode = boolean("amoled_dark_mode") { addNotices(FeatureNotice.UNSTABLE) }
    val mapFriendNameTags = boolean("map_friend_nametags")
    val streakExpirationInfo = boolean("streak_expiration_info")
    val hideStorySections = multiple("hide_story_sections",
        "hide_friend_suggestions", "hide_friends", "hide_following", "hide_for_you")
    val hideUiComponents = multiple("hide_ui_components",
        "hide_voice_record_button",
        "hide_stickers_button",
        "hide_cognac_button",
        "hide_live_location_share_button",
        "hide_call_buttons"
    )
    val disableSpotlight = boolean("disable_spotlight")
    val startupTab = unique("startup_tab",
        "ngs_map_icon_container",
        "ngs_chat_icon_container",
        "ngs_camera_icon_container",
        "ngs_community_icon_container",
        "ngs_spotlight_icon_container",
        "ngs_search_icon_container"
    ) { addNotices(FeatureNotice.MAY_BREAK_INTERNAL_BEHAVIOR) }
    val storyViewerOverride = unique("story_viewer_override", "DISCOVER_PLAYBACK_SEEKBAR", "VERTICAL_STORY_VIEWER") { addNotices(FeatureNotice.UNSTABLE) }
}
