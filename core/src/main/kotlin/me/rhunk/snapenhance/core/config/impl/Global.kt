package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice

class Global : ConfigContainer() {
    val snapchatPlus = boolean("snapchat_plus") { addNotices(FeatureNotice.BAN_RISK); requireRestart() }
    val disableMetrics = boolean("disable_metrics")
    val blockAds = boolean("block_ads")
    val bypassVideoLengthRestriction = unique("bypass_video_length_restriction", "split", "single") { addNotices(FeatureNotice.BAN_RISK); requireRestart() }
    val disableGooglePlayDialogs = boolean("disable_google_play_dialogs") { requireRestart() }
    val forceMediaSourceQuality = boolean("force_media_source_quality")
    val disableSnapSplitting = boolean("disable_snap_splitting") { addNotices(FeatureNotice.INTERNAL_BEHAVIOR) }
}