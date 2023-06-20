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
    val nameKey: String,
    val descriptionKey: String,
    val category: ConfigCategory,
    val valueContainer: ConfigValue<*>,
    val shouldAppearInSettings: Boolean = true,
    val disableValueLocalization: Boolean = false
) {

    //SPYING AND PRIVACY
    MESSAGE_LOGGER("property.message_logger",
        "description.message_logger",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_READ_RECEIPTS(
        "property.prevent_read_receipts",
        "description.prevent_read_receipts",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    HIDE_BITMOJI_PRESENCE(
        "property.hide_bitmoji_presence",
        "description.hide_bitmoji_presence",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    BETTER_NOTIFICATIONS(
        "property.better_notifications",
        "description.better_notifications",
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
        "property.notification_blacklist",
        "description.notification_blacklist",
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
    DISABLE_METRICS("property.disable_metrics",
        "description.disable_metrics",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    BLOCK_ADS("property.block_ads",
        "description.block_ads",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    UNLIMITED_SNAP_VIEW_TIME("property.unlimited_snap_view_time",
        "description.unlimited_snap_view_time",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_SCREENSHOT_NOTIFICATIONS(
        "property.prevent_screenshot_notifications",
        "description.prevent_screenshot_notifications",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_STATUS_NOTIFICATIONS(
        "property.prevent_status_notifications",
        "description.prevent_status_notifications",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    ANONYMOUS_STORY_VIEW(
        "property.anonymous_story_view",
        "description.anonymous_story_view",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    HIDE_TYPING_NOTIFICATION(
        "property.hide_typing_notification",
        "description.hide_typing_notification",
        ConfigCategory.SPYING_PRIVACY,
        ConfigStateValue(false)
    ),
    
    //MEDIA MANAGEMENT
    SAVE_FOLDER(
        "property.save_folder", "description.save_folder", ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStringValue(File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Snapchat",
            "SnapEnhance"
        ).absolutePath)
    ),
    AUTO_DOWNLOAD_OPTIONS(
        "property.auto_download_options", "description.auto_download_options", ConfigCategory.MEDIA_MANAGEMENT,
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
        "property.download_options", "description.download_options", ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateListValue(
            listOf("format_user_folder", "format_hash", "format_date_time", "format_username", "merge_overlay"),
            mutableMapOf(
                "format_user_folder" to true,
                "format_hash" to true,
                "format_date_time" to true,
                "format_username" to false,
                "merge_overlay" to false,
            )
        )
    ),
    CHAT_DOWNLOAD_CONTEXT_MENU(
        "property.chat_download_context_menu",
        "description.chat_download_context_menu",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    GALLERY_MEDIA_SEND_OVERRIDE(
        "property.gallery_media_send_override",
        "description.gallery_media_send_override",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateSelection(
            listOf("OFF", "NOTE", "SNAP", "LIVE_SNAP"),
            "OFF"
        )
    ),
    AUTO_SAVE_MESSAGES("property.auto_save_messages",
        "description.auto_save_messages",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateListValue(
            listOf("CHAT", "SNAP", "NOTE", "EXTERNAL_MEDIA", "STICKER")
        )
    ),

    FORCE_MEDIA_SOURCE_QUALITY(
        "property.force_media_source_quality",
        "description.force_media_source_quality",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    
    //UI AND TWEAKS
    ENABLE_FRIEND_FEED_MENU_BAR(
        "property.enable_friend_feed_menu_bar",
        "description.enable_friend_feed_menu_bar",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    FRIEND_FEED_MENU_BUTTONS(
        "property.friend_feed_menu_buttons",
        "description.friend_feed_menu_buttons",
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
    FRIEND_FEED_MENU_POSITION("property.friend_feed_menu_buttons_position",
        "description.friend_feed_menu_buttons_position",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(1)
    ),
    HIDE_UI_ELEMENTS(
        "property.hide_ui_elements",
        "description.hide_ui_elements",
        ConfigCategory.UI_TWEAKS,
        ConfigStateListValue(
            listOf("remove_voice_record_button", "remove_stickers_button", "remove_cognac_button", "remove_call_buttons", "remove_camera_borders", "remove_stories_button", "remove_spotlight_button"),
            mutableMapOf(
                "remove_voice_record_button" to false,
                "remove_stickers_button" to false,
                "remove_cognac_button" to false,
                "remove_call_buttons" to false,
                "remove_camera_borders" to false,
                "remove_stories_button" to false,
                "remove_spotlight_button" to false
            )
        )
    ),
    STREAK_EXPIRATION_INFO(
        "property.streak_expiration_info",
        "description.streakexpirationinfo",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_SNAP_SPLITTING(
        "property.disable_snap_splitting",
        "description.disable_snap_splitting",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_VIDEO_LENGTH_RESTRICTION(
        "property.disable_video_length_restriction",
        "description.disable_video_length_restriction",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    SNAPCHAT_PLUS("property.snapchat_plus",
        "description.snapchat_plus",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    NEW_MAP_UI("property.new_map_ui",
        "description.new_map_ui",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    LOCATION_SPOOF(
        "property.location_spoof",
        "description.location_spoof",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    LATITUDE(
        "property.latitude_value",
        "description.latitude_value",
        ConfigCategory.UI_TWEAKS,
        ConfigStringValue("0.0000"),
        shouldAppearInSettings = false
    ),
    LONGITUDE(
        "property.longitude_value",
        "description.longitude_value",
        ConfigCategory.UI_TWEAKS,
        ConfigStringValue("0.0000"),
        shouldAppearInSettings = false
    ),
    MESSAGE_PREVIEW_LENGTH(
        "property.message_preview_length",
        "description.message_preview_length",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(20)
    ),
    UNLIMITED_CONVERSATION_PINNING(
        "property.unlimited_conversation_pinning",
        "description.unlimited_conversation_pinning",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_SPOTLIGHT(
        "property.disable_spotlight",
        "description.disable_spotlight",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    ENABLE_APP_APPEARANCE(
        "property.enable_app_appearance",
        "description.enable_app_appearance",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(true)
    ),


    //CAMERA
    CAMERA_DISABLE(
        "property.disable_camera",
        "description.disable_camera",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    IMMERSIVE_CAMERA_PREVIEW(
        "property.immersive_camera_preview",
        "description.immersive_camera_preview",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    OVERRIDE_PREVIEW_RESOLUTION(
        "property.preview_resolution",
        "description.preview_resolution",
        ConfigCategory.CAMERA,
        ConfigStateSelection(
            CameraTweaks.resolutions,
            "OFF"
        ),
        disableValueLocalization = true
    ),
    OVERRIDE_PICTURE_RESOLUTION(
        "property.picture_resolution",
        "description.picture_resolution",
        ConfigCategory.CAMERA,
        ConfigStateSelection(
            CameraTweaks.resolutions,
            "OFF"
        ),
        disableValueLocalization = true
    ),
    FORCE_HIGHEST_FRAME_RATE(
        "property.force_highest_frame_rate",
        "description.force_highest_frame_rate",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),
    FORCE_CAMERA_SOURCE_ENCODING(
        "property.force_camera_source_encoding",
        "description.force_camera_source_encoding",
        ConfigCategory.CAMERA,
        ConfigStateValue(false)
    ),

    // UPDATES
    AUTO_UPDATER(
        "property.auto_updater",
        "description.auto_updater",
        ConfigCategory.UPDATES,
        ConfigStateSelection(
            listOf("DISABLED", "EVERY_LAUNCH", "DAILY", "WEEKLY"),
            "DAILY"
        )
    ),

    // EXPERIMENTAL DEBUGGING
    APP_PASSCODE(
        "property.app_passcode",
        "description.app_passcode",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStringValue("", isHidden = true)
    ),
    APP_LOCK_ON_RESUME(
        "property.app_lock_on_resume",
        "description.app_lock_on_resume",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    INFINITE_STORY_BOOST(
        "property.infinite_story_boost",
        "description.infinite_story_boost",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    MEO_PASSCODE_BYPASS(
    "property.meo_passcode_bypass",
    "description.meo_passcode_bypass",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
    AMOLED_DARK_MODE(
        "property.amoled_dark_mode",
        "description.amoled_dark_mode",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    );

    companion object {
        fun sortedByCategory(): List<ConfigProperty> {
            return values().sortedBy { it.category.ordinal }
        }
    }
}