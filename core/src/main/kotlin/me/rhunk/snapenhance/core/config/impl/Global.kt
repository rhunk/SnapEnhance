package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.data.NotificationType

class Global : ConfigContainer() {
    val snapchatPlus = boolean("snapchat_plus") { addNotices(FeatureNotice.MAY_BAN) }
    val autoUpdater = unique("auto_updater", "EVERY_LAUNCH", "DAILY", "WEEKLY").apply { set("DAILY") }
    val disableMetrics = boolean("disable_metrics")
    val blockAds = boolean("block_ads")
    val disableVideoLengthRestrictions = boolean("disable_video_length_restrictions") { addNotices(FeatureNotice.MAY_BAN) }
    val disableGooglePlayDialogs = boolean("disable_google_play_dialogs")
    val forceMediaSourceQuality = boolean("force_media_source_quality")
    val betterNotifications = multiple("better_notifications", "snap", "chat", "reply_button", "download_button")
    val notificationBlacklist = multiple("notification_blacklist", *NotificationType.getIncomingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
    }
    val disableSnapSplitting = boolean("disable_snap_splitting") { addNotices(FeatureNotice.MAY_BREAK_INTERNAL_BEHAVIOR) }
}