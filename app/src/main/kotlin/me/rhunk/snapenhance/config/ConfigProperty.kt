package me.rhunk.snapenhance.config

import android.os.Environment
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import java.io.File

enum class ConfigProperty(
    val nameKey: String,
    val descriptionKey: String,
    val category: ConfigCategory,
    val valueContainer: ConfigValue<*>,
    val shouldAppearInSettings: Boolean = true
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
    DOWNLOAD_BLACKLIST(
        "property.auto_download_blacklist",
        "description.auto_download_blacklist",
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
    ANTI_AUTO_SAVE("property.anti_auto_save",
        "description.anti_auto_save",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    
    OVERRIDE_MEDIA_QUALITY(
        "property.override_media_quality",
        "description.override_media_quality",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateValue(false)
    ),
    MEDIA_QUALITY_LEVEL(
        "property.media_quality_level",
        "description.media_quality_level",
        ConfigCategory.MEDIA_MANAGEMENT,
        ConfigStateSelection(
            listOf("LEVEL_NONE", "LEVEL_1", "LEVEL_2", "LEVEL_3", "LEVEL_4", "LEVEL_5", "LEVEL_6", "LEVEL_7", "LEVEL_MAX"),
            "LEVEL_NONE"
        )
    ),
    
    //UI AND TWEAKS
    CAMERA_DISABLE(
        "property.disable_camera",
        "description.disable_camera",
        ConfigCategory.UI_TWEAKS,
        ConfigStateValue(false)
    ),
    HIDE_UI_ELEMENTS(
        "property.hide_ui_elements",
        "description.hide_ui_elements",
        ConfigCategory.UI_TWEAKS,
        ConfigStateListValue(
            listOf("remove_voice_record_button", "remove_stickers_button", "remove_cognac_button", "remove_call_buttons"),
            mutableMapOf(
                "remove_voice_record_button" to false,
                "remove_stickers_button" to false,
                "remove_cognac_button" to false,
                "remove_call_buttons" to false,
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
    MENU_SLOT_ID("property.menu_slot_id",
        "description.menu_slot_id",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(1)
    ),
    MESSAGE_PREVIEW_LENGTH(
        "property.message_preview_length",
        "description.message_preview_length",
        ConfigCategory.UI_TWEAKS,
        ConfigIntegerValue(20)
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
    USE_DOWNLOAD_MANAGER(
        "property.use_download_manager",
        "description.use_download_manager",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    ),
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
    MEO_PASSCODE_BYPASS(
    "property.meo_passcode_bypass",
    "description.meo_passcode_bypass",
        ConfigCategory.EXPERIMENTAL_DEBUGGING,
        ConfigStateValue(false)
    );

    companion object {
        fun sortedByCategory(): List<ConfigProperty> {
            return values().sortedBy { it.category.ordinal }
        }
    }
}