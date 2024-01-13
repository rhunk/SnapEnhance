package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.FeatureNotice

class Global : ConfigContainer() {
    inner class SpoofLocation : ConfigContainer(hasGlobalState = true) {
        val coordinates = mapCoordinates("coordinates", 0.0 to 0.0) { requireRestart()} // lat, long
    }
    val spoofLocation = container("spoofLocation", SpoofLocation())
    val suspendLocationUpdates = boolean("suspend_location_updates") { requireRestart() }
    val snapchatPlus = boolean("snapchat_plus") { requireRestart() }
    val disableConfirmationDialogs = multiple("disable_confirmation_dialogs", "remove_friend", "block_friend", "ignore_friend", "hide_friend", "hide_conversation", "clear_conversation") { requireRestart() }
    val disableMetrics = boolean("disable_metrics") { requireRestart() }
    val disableStorySections = multiple("disable_story_sections", "friends", "following", "discover") { requireRestart(); requireCleanCache() }
    val blockAds = boolean("block_ads")
    val spotlightCommentsUsername = boolean("spotlight_comments_username") { requireRestart() }
    val bypassVideoLengthRestriction = unique("bypass_video_length_restriction", "split", "single") { addNotices(
        FeatureNotice.BAN_RISK); requireRestart(); nativeHooks() }
    val disableGooglePlayDialogs = boolean("disable_google_play_dialogs") { requireRestart() }
    val forceUploadSourceQuality = boolean("force_upload_source_quality") { requireRestart() }
    val disableSnapSplitting = boolean("disable_snap_splitting") { addNotices(FeatureNotice.INTERNAL_BEHAVIOR) }
}