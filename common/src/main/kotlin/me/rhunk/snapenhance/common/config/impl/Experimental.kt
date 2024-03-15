package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.FeatureNotice

class Experimental : ConfigContainer() {
    class SessionEventsConfig : ConfigContainer(hasGlobalState = true) {
        val captureDuplexEvents = boolean("capture_duplex_events", true)
        val allowRunningInBackground = boolean("allow_running_in_background", true)
    }

    class NativeHooks : ConfigContainer(hasGlobalState = true) {
        val disableBitmoji = boolean("disable_bitmoji")
        val remapApk = boolean("remap_apk") { addNotices(FeatureNotice.UNSTABLE) }
    }

    class E2EEConfig : ConfigContainer(hasGlobalState = true) {
        val encryptedMessageIndicator = boolean("encrypted_message_indicator")
        val forceMessageEncryption = boolean("force_message_encryption")
    }

    class AccountSwitcherConfig : ConfigContainer(hasGlobalState = true) {
        val autoBackupCurrentAccount = boolean("auto_backup_current_account", defaultValue = true)
    }

    val nativeHooks = container("native_hooks", NativeHooks()) { icon = "Memory"; requireRestart() }
    val sessionEvents = container("session_events", SessionEventsConfig()) { requireRestart(); nativeHooks() }
    val spoof = container("spoof", Spoof()) { icon = "Fingerprint" ; addNotices(FeatureNotice.BAN_RISK); requireRestart() }
    val convertMessageLocally = boolean("convert_message_locally") { requireRestart() }
    val newChatActionMenu = boolean("new_chat_action_menu") { requireRestart() }
    val storyLogger = boolean("story_logger") { requireRestart(); addNotices(FeatureNotice.UNSTABLE); }
    val callRecorder = boolean("call_recorder") { requireRestart(); addNotices(FeatureNotice.UNSTABLE); }
    val accountSwitcher = container("account_switcher", AccountSwitcherConfig()) { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val editMessage = boolean("edit_message") { requireRestart(); addNotices(FeatureNotice.BAN_RISK) }
    val appPasscode = string("app_passcode")
    val appLockOnResume = boolean("app_lock_on_resume")
    val infiniteStoryBoost = boolean("infinite_story_boost")
    val meoPasscodeBypass = boolean("meo_passcode_bypass")
    val noFriendScoreDelay = boolean("no_friend_score_delay") { requireRestart()}
    val e2eEncryption = container("e2ee", E2EEConfig()) { requireRestart(); nativeHooks() }
    val hiddenSnapchatPlusFeatures = boolean("hidden_snapchat_plus_features") {
        addNotices(FeatureNotice.BAN_RISK, FeatureNotice.UNSTABLE)
        requireRestart()
    }
    val addFriendSourceSpoof = unique("add_friend_source_spoof",
        "added_by_username",
        "added_by_mention",
        "added_by_group_chat",
        "added_by_qr_code",
        "added_by_community",
    ) { addNotices(FeatureNotice.BAN_RISK) }
    val disableComposerModules = string("disable_composer_modules") { requireRestart(); nativeHooks() }
    val preventForcedLogout = boolean("prevent_forced_logout") { requireRestart(); addNotices(FeatureNotice.BAN_RISK, FeatureNotice.INTERNAL_BEHAVIOR); }
}