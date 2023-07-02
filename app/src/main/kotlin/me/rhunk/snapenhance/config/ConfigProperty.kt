package me.rhunk.snapenhance.config

import android.os.Environment
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.features.impl.tweaks.CameraTweaks
import java.io.File

enum class ConfigProperty(
    val translationKey: String,
    val category: ConfigCategory,
    val valueContainer: ConfigValue<*>,
    val shouldAppearInSettings: Boolean = true,
    val disableValueLocalization: Boolean = false
) {

    //SPYING AND PRIVACY
    MESSAGE_LOGGER("message_logger",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_READ_RECEIPTS(
        "prevent_read_receipts",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    HIDE_BITMOJI_PRESENCE(
        "hide_bitmoji_presence",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    BETTER_NOTIFICATIONS(
        "better_notifications",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateListValue(
            listOf("snap", "chat", "reply_button"),
            mutableMapOf(
                "snap" to false,
                "chat" to false,
                "reply_button" to false
            )
        )
    ),
    NOTIFICATION_BLACKLIST(
        "notification_blacklist",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateListValue(
            listOf("snap", "chat", "typing"),
            mutableMapOf(
                "snap" to false,
                "chat" to false,
                "typing" to false
            )
        )
    ),
    DISABLE_METRICS("disable_metrics",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    BLOCK_ADS("block_ads",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    UNLIMITED_SNAP_VIEW_TIME("unlimited_snap_view_time",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_SCREENSHOT_NOTIFICATIONS(
        "prevent_screenshot_notifications",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_STATUS_NOTIFICATIONS(
        "prevent_status_notifications",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    ANONYMOUS_STORY_VIEW(
        "anonymous_story_view",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    HIDE_TYPING_NOTIFICATION(
        "hide_typing_notification",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    
    //MEDIA MANAGEMENT
    SAVE_FOLDER(
        "save_folder",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStringValue(File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Snapchat",
            "SnapEnhance"
        ).absolutePath)
    ),
    AUTO_DOWNLOAD_OPTIONS(
        "auto_download_options",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateListValue(
            listOf("friend_snaps", "friend_stories", "public_stories", "spotlight"),
            mutableMapOf(
                "friend_snaps" to false,
                "friend_stories" to false,
                "public_stories" to false,
                "spotlight" to false
            )
        )
    ),
    DOWNLOAD_OPTIONS(
        "download_options",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateListValue(
            listOf(
                "allow_duplicate",
                "create_user_folder",
                "append_hash",
                "append_date_time",
                "append_type",
                "append_username",
                "merge_overlay"
            ),
            mutableMapOf(
                "allow_duplicate" to false,
                "create_user_folder" to true,
                "append_hash" to true,
                "append_date_time" to true,
                "append_type" to false,
                "append_username" to false,
                "merge_overlay" to false,
            )
        )
    ),
    CHAT_DOWNLOAD_CONTEXT_MENU(
        "chat_download_context_menu",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    GALLERY_MEDIA_SEND_OVERRIDE(
        "gallery_media_send_override",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateSelection(
            listOf("OFF", "NOTE", "SNAP", "LIVE_SNAP"),
            "OFF"
        )
    ),
    AUTO_SAVE_MESSAGES("auto_save_messages",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateListValue(
            listOf("CHAT", "SNAP", "NOTE", "EXTERNAL_MEDIA", "STICKER")
        )
    ),

    FORCE_MEDIA_SOURCE_QUALITY(
        "force_media_source_quality",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    
    //UI AND TWEAKS
    ENABLE_FRIEND_FEED_MENU_BAR(
        "enable_friend_feed_menu_bar",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    FRIEND_FEED_MENU_BUTTONS(
        "friend_feed_menu_buttons",
        ConfigCategory.UI_TWEAKS,
        ConfigStateListValue(
            listOf("auto_download_blacklist", "anti_auto_save", "stealth_mode", "conversation_info"),
            mutableMapOf(
                "auto_download_blacklist" to false,
                "anti_auto_save" to false,
                "stealth_mode" to true,
                "conversation_info" to true
            )
        )
    ),
    FRIEND_FEED_MENU_POSITION("friend_feed_menu_buttons_position",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(1)
    ),
    HIDE_UI_ELEMENTS(
        "hide_ui_elements",
        ConfigCategory.UI_TWEAKS,
        ConfigStateListValue(
            listOf("remove_voice_record_button", "remove_stickers_button", "remove_cognac_button", "remove_live_location_share_button", "remove_call_buttons", "remove_camera_borders"),
            mutableMapOf(
                "remove_voice_record_button" to false,
                "remove_stickers_button" to false,
                "remove_cognac_button" to false,
                "remove_live_location_share_button" to false,
                "remove_call_buttons" to false,
                "remove_camera_borders" to false
            )
        )
    ),
    HIDE_STORY_SECTION(
        "hide_story_section",
        ConfigCategory.UI_TWEAKS,
        ConfigStateListValue(
            listOf("hide_friends", "hide_following", "hide_for_you"),
            mutableMapOf(
                "hide_friends" to false,
                "hide_following" to false,
                "hide_for_you" to false
            )
        )
    ),
    STORY_VIEWER_OVERRIDE("story_viewer_override",
        ConfigCategory.UI_TWEAKS,
        ConfigStateSelection(
            listOf("OFF", "DISCOVER_PLAYBACK_SEEKBAR", "VERTICAL_STORY_VIEWER"),
            "OFF"
        )
    ),
    STREAK_EXPIRATION_INFO(
        "streak_expiration_info",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_SNAP_SPLITTING(
        "disable_snap_splitting",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_VIDEO_LENGTH_RESTRICTION(
        "disable_video_length_restriction",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    SNAPCHAT_PLUS("snapchat_plus",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    NEW_MAP_UI("new_map_ui",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    LOCATION_SPOOF(
        "location_spoof",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    LATITUDE(
        "latitude_value",
        ConfigCategory.UI_TWEAKS,
        ConfigStringValue("0.0000"),
        shouldAppearInSettings = false
    ),
    LONGITUDE(
        "longitude_value",
        ConfigCategory.UI_TWEAKS,
        ConfigStringValue("0.0000"),
        shouldAppearInSettings = false
    ),
    MESSAGE_PREVIEW_LENGTH(
        "message_preview_length",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(20)
    ),
    UNLIMITED_CONVERSATION_PINNING(
        "unlimited_conversation_pinning",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_SPOTLIGHT(
        "disable_spotlight",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    ENABLE_APP_APPEARANCE(
        "enable_app_appearance",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    STARTUP_PAGE_OVERRIDE(
        "startup_page_override",
        ConfigCategory.UI_TWEAKS,
        ConfigStateSelection(
            listOf(
                "OFF",
                "ngs_map_icon_container",
                "ngs_chat_icon_container",
                "ngs_camera_icon_container",
                "ngs_community_icon_container",
                "ngs_spotlight_icon_container",
                "ngs_search_icon_container"
            ),
            "OFF"
        )
    ),


    //CAMERA
    CAMERA_DISABLE(
        "disable_camera",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    IMMERSIVE_CAMERA_PREVIEW(
        "immersive_camera_preview",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    OVERRIDE_PREVIEW_RESOLUTION(
        "preview_resolution",
        ConfigCategory.CAMERA,
        ConfigStateSelection(
            CameraTweaks.resolutions,
            "OFF"
        ),
        disableValueLocalization = true
    ),
    OVERRIDE_PICTURE_RESOLUTION(
        "picture_resolution",
        ConfigCategory.CAMERA,
        ConfigStateSelection(
            CameraTweaks.resolutions,
            "OFF"
        ),
        disableValueLocalization = true
    ),
    FORCE_HIGHEST_FRAME_RATE(
        "force_highest_frame_rate",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    FORCE_CAMERA_SOURCE_ENCODING(
        "force_camera_source_encoding",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),

    // UPDATES
    AUTO_UPDATER(
        "auto_updater",
        ConfigCategory.UPDATES,
        ConfigStateSelection(
            listOf("DISABLED", "EVERY_LAUNCH", "DAILY", "WEEKLY"),
            "DAILY"
        )
    ),

    // EXPERIMENTAL DEBUGGING
    APP_PASSCODE(
        "app_passcode",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStringValue("", isHidden = true)
    ),
    APP_LOCK_ON_RESUME(
        "app_lock_on_resume",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    INFINITE_STORY_BOOST(
        "infinite_story_boost",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    MEO_PASSCODE_BYPASS(
    "meo_passcode_bypass",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    AMOLED_DARK_MODE(
        "amoled_dark_mode",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    UNLIMITED_MULTI_SNAP(
        "unlimited_multi_snap",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    );

    companion object {
        fun sortedByCategory(): List<ConfigProperty> {
            return values().sortedBy { it.category.ordinal }
        }
    }
}