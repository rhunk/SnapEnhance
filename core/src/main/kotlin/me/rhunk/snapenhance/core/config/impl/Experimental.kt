package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class Experimental : ConfigContainer() {
    val nativeHooks = container("native_hooks", NativeHooks()) { icon = "Memory" }
    val spoof = container("spoof", Spoof()) { icon = "Fingerprint" }
    val appPasscode = string("app_passcode")
    val appLockOnResume = boolean("app_lock_on_resume")
    val infiniteStoryBoost = boolean("infinite_story_boost")
    val meoPasscodeBypass = boolean("meo_passcode_bypass")
    val unlimitedMultiSnap = boolean("unlimited_multi_snap")
    val noFriendScoreDelay = boolean("no_friend_score_delay")
}