package me.rhunk.snapenhance.config

import android.os.Environment
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import java.io.File

enum class ConfigProperty(
    val nameKey: String,
    val descriptionKey: String,
    val category: ConfigCategory,
    val valueContainer: ConfigValue<*>
) {
    SAVE_FOLDER(
        "property.save_folder", "description.save_folder", ConfigCategory.GENERAL,
        ConfigStringValue(File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Snapchat",
            "SnapEnhance"
        ).absolutePath)
    ),

    PREVENT_READ_RECEIPTS(
        "property.prevent_read_receipts",
        "description.prevent_read_receipts",
        ConfigCategory.SPY,
        ConfigStateValue(false)
    ),
    HIDE_BITMOJI_PRESENCE(
        "property.hide_bitmoji_presence",
        "description.hide_bitmoji_presence",
        ConfigCategory.SPY,
        ConfigStateValue(false)
    ),
    SHOW_MESSAGE_CONTENT_IN_NOTIFICATIONS(
        "property.show_message_content_in_notifications",
        "description.show_message_content_in_notifications",
        ConfigCategory.SPY,
        ConfigStateValue(false)
    ),
    NOTIFICATION_FILTER(
        "property.notification_filter",
        "description.notification_filter",
        ConfigCategory.SPY,
        ConfigStateListValue(
            listOf("snap", "chat", "typing"),
            mutableMapOf(
                "snap" to true,
                "chat" to true,
                "typing" to true
            )
        )
    ),

    MESSAGE_LOGGER("property.message_logger", "description.message_logger", ConfigCategory.SPY, ConfigStateValue(false)),
    UNLIMITED_SNAP_VIEW_TIME("property.unlimited_snap_view_time", "description.unlimited_snap_view_time", ConfigCategory.SPY, ConfigStateValue(false)),

    AUTO_DOWNLOAD_SNAPS(
        "property.auto_download_snaps",
        "description.auto_download_snaps",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    AUTO_DOWNLOAD_STORIES(
        "property.auto_download_stories",
        "description.auto_download_stories",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    AUTO_DOWNLOAD_PUBLIC_STORIES(
        "property.auto_download_public_stories",
        "description.auto_download_public_stories",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    AUTO_DOWNLOAD_SPOTLIGHT(
        "property.auto_download_spotlight",
        "description.auto_download_spotlight",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    OVERLAY_MERGE(
        "property.overlay_merge",
        "description.overlay_merge",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    DOWNLOAD_INCHAT_SNAPS(
        "property.download_inchat_snaps",
        "description.download_inchat_snaps",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),
    ANTI_DOWNLOAD_BUTTON(
        "property.anti_auto_download_button",
        "description.anti_auto_download_button",
        ConfigCategory.MEDIA_DOWNLOADER,
        ConfigStateValue(false)
    ),

    DISABLE_METRICS("property.disable_metrics", "description.disable_metrics", ConfigCategory.PRIVACY, ConfigStateValue(false)),
    PREVENT_SCREENSHOT_NOTIFICATIONS(
        "property.prevent_screenshot_notifications",
        "description.prevent_screenshot_notifications",
        ConfigCategory.PRIVACY,
        ConfigStateValue(false)
    ),
    PREVENT_STATUS_NOTIFICATIONS(
        "property.prevent_status_notifications",
        "description.prevent_status_notifications",
        ConfigCategory.PRIVACY,
        ConfigStateValue(false)
    ),
    ANONYMOUS_STORY_VIEW(
        "property.anonymous_story_view",
        "description.anonymous_story_view",
        ConfigCategory.PRIVACY,
        ConfigStateValue(false)
    ),
    HIDE_TYPING_NOTIFICATION(
        "property.hide_typing_notification",
        "description.hide_typing_notification",
        ConfigCategory.PRIVACY,
        ConfigStateValue(false)
    ),

    MENU_SLOT_ID("property.menu_slot_id", "description.menu_slot_id", ConfigCategory.UI, ConfigIntegerValue(1)),
    MESSAGE_PREVIEW_LENGTH(
        "property.message_preview_length",
        "description.message_preview_length",
        ConfigCategory.UI,
        ConfigIntegerValue(20)
    ),

    EXTERNAL_MEDIA_AS_SNAP(
        "property.external_media_as_snap",
        "description.external_media_as_snap",
        ConfigCategory.EXTRAS,
        ConfigStateValue(false)
    ),
    AUTO_SAVE("property.auto_save", "description.auto_save", ConfigCategory.EXTRAS, ConfigStateValue(false)),
    ANTI_AUTO_SAVE("property.anti_auto_save", "description.anti_auto_save", ConfigCategory.EXTRAS, ConfigStateValue(false)),
    SNAPCHAT_PLUS("property.snapchat_plus", "description.snapchat_plus", ConfigCategory.EXTRAS, ConfigStateValue(false)),

    REMOVE_VOICE_RECORD_BUTTON(
        "property.remove_voice_record_button",
        "description.remove_voice_record_button",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    REMOVE_STICKERS_BUTTON(
        "property.remove_stickers_button",
        "description.remove_stickers_button",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    REMOVE_COGNAC_BUTTON(
        "property.remove_cognac_button",
        "description.remove_cognac_button",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    REMOVE_CALL_BUTTONS(
        "property.remove_call_buttons",
        "description.remove_call_buttons",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    DISABLE_SNAP_SPLITTING(
        "property.disable_snap_splitting",
        "description.disable_snap_splitting",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    BLOCK_ADS("property.block_ads", "description.block_ads", ConfigCategory.TWEAKS, ConfigStateValue(false)),
    STREAK_EXPIRATION_INFO(
        "property.streak_expiration_info",
        "description.streakexpirationinfo",
        ConfigCategory.TWEAKS,
        ConfigStateValue(false)
    ),
    NEW_MAP_UI("property.new_map_ui", "description.new_map_ui", ConfigCategory.TWEAKS, ConfigStateValue(false)),

    USE_DOWNLOAD_MANAGER(
        "property.use_download_manager",
        "description.use_download_manager",
        ConfigCategory.EXPERIMENTAL,
        ConfigStateValue(false)
    );

    companion object {
        fun fromNameKey(nameKey: String): ConfigProperty? {
            return values().find { it.nameKey == nameKey }
        }

        fun sortedByCategory(): List<ConfigProperty> {
            return values().sortedBy { it.category.ordinal }
        }
    }
}