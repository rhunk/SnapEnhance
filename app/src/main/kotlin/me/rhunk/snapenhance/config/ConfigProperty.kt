package me.rhunk.snapenhance.config

import android.os.Environment
import java.io.File

enum class ConfigProperty(
    val nameKey: String,
    val descriptionKey: String,
    val category: ConfigCategory,
    val defaultValue: Any
) {
    SAVE_FOLDER(
        "save_folder", "description.save_folder", ConfigCategory.GENERAL,
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Snapchat",
            "SnapEnhance"
        ).absolutePath
    ),

    PREVENT_READ_RECEIPTS(
        "prevent_read_receipts",
        "description.prevent_read_receipts",
        ConfigCategory.SPY,
        false
    ),
    HIDE_BITMOJI_PRESENCE(
        "hide_bitmoji_presence",
        "description.hide_bitmoji_presence",
        ConfigCategory.SPY,
        false
    ),
    SHOW_MESSAGE_CONTENT(
        "show_message_content",
        "description.show_message_content",
        ConfigCategory.SPY,
        false
    ),
    MESSAGE_LOGGER("message_logger", "description.message_logger", ConfigCategory.SPY, false),

    MEDIA_DOWNLOADER_FEATURE(
        "media_downloader_feature",
        "description.media_downloader_feature",
        ConfigCategory.MEDIA_DOWNLOADER,
        true
    ),
    DOWNLOAD_STORIES(
        "download_stories",
        "description.download_stories",
        ConfigCategory.MEDIA_DOWNLOADER,
        false
    ),
    DOWNLOAD_PUBLIC_STORIES(
        "download_public_stories",
        "description.download_public_stories",
        ConfigCategory.MEDIA_DOWNLOADER,
        false
    ),
    DOWNLOAD_SPOTLIGHT(
        "download_spotlight",
        "description.download_spotlight",
        ConfigCategory.MEDIA_DOWNLOADER,
        false
    ),
    OVERLAY_MERGE(
        "overlay_merge",
        "description.overlay_merge",
        ConfigCategory.MEDIA_DOWNLOADER,
        true
    ),
    DOWNLOAD_INCHAT_SNAPS(
        "download_inchat_snaps",
        "description.download_inchat_snaps",
        ConfigCategory.MEDIA_DOWNLOADER,
        true
    ),

    DISABLE_METRICS("disable_metrics", "description.disable_metrics", ConfigCategory.PRIVACY, true),
    PREVENT_SCREENSHOTS(
        "prevent_screenshots",
        "description.prevent_screenshots",
        ConfigCategory.PRIVACY,
        true
    ),
    PREVENT_STATUS_NOTIFICATIONS(
        "prevent_status_notifications",
        "description.prevent_status_notifications",
        ConfigCategory.PRIVACY,
        true
    ),
    ANONYMOUS_STORY_VIEW(
        "anonymous_story_view",
        "description.anonymous_story_view",
        ConfigCategory.PRIVACY,
        false
    ),
    HIDE_TYPING_NOTIFICATION(
        "hide_typing_notification",
        "description.hide_typing_notification",
        ConfigCategory.PRIVACY,
        false
    ),

    MENU_SLOT_ID("menu_slot_id", "description.menu_slot_id", ConfigCategory.UI, 1),
    MESSAGE_PREVIEW_LENGTH(
        "message_preview_length",
        "description.message_preview_length",
        ConfigCategory.UI,
        20
    ),

    AUTO_SAVE("auto_save", "description.auto_save", ConfigCategory.EXTRAS, false),
    /*EXTERNAL_MEDIA_AS_SNAP(
        "external_media_as_snap",
        "description.external_media_as_snap",
        ConfigCategory.EXTRAS,
        false
    ),
    CONVERSATION_EXPORT(
        "conversation_export",
        "description.conversation_export",
        ConfigCategory.EXTRAS,
        false
    ),*/
    SNAPCHAT_PLUS("snapchat_plus", "description.snapchat_plus", ConfigCategory.EXTRAS, false),

    REMOVE_VOICE_RECORD_BUTTON(
        "remove_voice_record_button",
        "description.remove_voice_record_button",
        ConfigCategory.TWEAKS,
        false
    ),
    REMOVE_STICKERS_BUTTON(
        "remove_stickers_button",
        "description.remove_stickers_button",
        ConfigCategory.TWEAKS,
        false
    ),
    REMOVE_COGNAC_BUTTON(
        "remove_cognac_button",
        "description.remove_cognac_button",
        ConfigCategory.TWEAKS,
        false
    ),
    REMOVE_CALLBUTTONS(
        "remove_callbuttons",
        "description.remove_callbuttons",
        ConfigCategory.TWEAKS,
        false
    ),
    LONG_SNAP_SENDING(
        "long_snap_sending",
        "description.long_snap_sending",
        ConfigCategory.TWEAKS,
        false
    ),
    BLOCK_ADS("block_ads", "description.block_ads", ConfigCategory.TWEAKS, false),
    STREAKEXPIRATIONINFO(
        "streakexpirationinfo",
        "description.streakexpirationinfo",
        ConfigCategory.TWEAKS,
        false
    ),
    NEW_MAP_UI("new_map_ui", "description.new_map_ui", ConfigCategory.TWEAKS, false),

    USE_DOWNLOAD_MANAGER(
        "use_download_manager",
        "description.use_download_manager",
        ConfigCategory.EXPERIMENTS,
        false
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