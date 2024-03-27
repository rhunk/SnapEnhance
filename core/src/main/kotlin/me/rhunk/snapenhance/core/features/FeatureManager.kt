package me.rhunk.snapenhance.core.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.impl.COFOverride
import me.rhunk.snapenhance.core.features.impl.ConfigurationOverride
import me.rhunk.snapenhance.core.features.impl.MixerStories
import me.rhunk.snapenhance.core.features.impl.OperaViewerParamsOverride
import me.rhunk.snapenhance.core.features.impl.ScopeSync
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.ProfilePictureDownloader
import me.rhunk.snapenhance.core.features.impl.experiments.*
import me.rhunk.snapenhance.core.features.impl.global.*
import me.rhunk.snapenhance.core.features.impl.messaging.*
import me.rhunk.snapenhance.core.features.impl.spying.HalfSwipeNotifier
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.features.impl.tweaks.*
import me.rhunk.snapenhance.core.features.impl.ui.*
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.ui.menu.MenuViewInjector
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

class FeatureManager(
    private val context: ModContext
) {
    private val features = mutableMapOf<KClass<out Feature>, Feature>()

    private fun register(vararg featureList: Feature) {
        if (context.bridgeClient.getDebugProp("disable_feature_loading") == "true") {
            context.log.warn("Feature loading is disabled")
            return
        }

        runBlocking {
            featureList.forEach { feature ->
                launch(Dispatchers.IO) {
                    runCatching {
                        feature.context = context
                        synchronized(features) {
                            features[feature::class] = feature
                        }
                    }.onFailure {
                        CoreLogger.xposedLog("Failed to register feature ${feature.featureKey}", it)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Feature> get(featureClass: KClass<T>): T? {
        return features[featureClass] as? T
    }

    fun getRuleFeatures() = features.values.filterIsInstance<MessagingRuleFeature>().sortedBy { it.ruleType.ordinal }

    fun init() {
        register(
            EndToEndEncryption(),
            ScopeSync(),
            PreventMessageListAutoScroll(),
            Messaging(),
            MediaDownloader(),
            StealthMode(),
            MenuViewInjector(),
            PreventReadReceipts(),
            MessageLogger(),
            ConvertMessageLocally(),
            SnapchatPlus(),
            DisableMetrics(),
            PreventMessageSending(),
            Notifications(),
            AutoSave(),
            UITweaks(),
            ConfigurationOverride(),
            COFOverride(),
            UnsaveableMessages(),
            SendOverride(),
            UnlimitedSnapViewTime(),
            BypassVideoLengthRestriction(),
            MediaQualityLevelOverride(),
            MeoPasscodeBypass(),
            AppPasscode(),
            CameraTweaks(),
            InfiniteStoryBoost(),
            PinConversations(),
            DeviceSpooferHook(),
            ClientBootstrapOverride(),
            GooglePlayServicesDialogs(),
            NoFriendScoreDelay(),
            ProfilePictureDownloader(),
            AddFriendSourceSpoof(),
            DisableReplayInFF(),
            OldBitmojiSelfie(),
            FriendFeedMessagePreview(),
            HideStreakRestore(),
            HideFriendFeedEntry(),
            HideQuickAddFriendFeed(),
            CallStartConfirmation(),
            SnapPreview(),
            InstantDelete(),
            BypassScreenshotDetection(),
            HalfSwipeNotifier(),
            DisableConfirmationDialogs(),
            MixerStories(),
            DisableComposerModules(),
            MessageIndicators(),
            EditTextOverride(),
            PreventForcedLogout(),
            SuspendLocationUpdates(),
            ConversationToolbox(),
            SpotlightCommentsUsername(),
            OperaViewerParamsOverride(),
            StealthModeIndicator(),
            DisablePermissionRequests(),
            SessionEvents(),
            DefaultVolumeControls(),
            CallRecorder(),
            DisableMemoriesSnapFeed(),
            AccountSwitcher(),
            RemoveGroupsLockedStatus(),
            BypassMessageActionRestrictions(),
            CustomizeUI(),
            BetterLocation(),
            MediaFilePicker(),
        )
    
        initializeFeatures()
    }

    private inline fun tryInit(feature: Feature, crossinline block: () -> Unit) {
        runCatching {
            block()
        }.onFailure {
            context.log.error("Failed to init feature ${feature.featureKey}", it)
            context.longToast("Failed to init feature ${feature.featureKey}! Check logcat for more details.")
        }
    }

    private fun initFeatures(
        syncParam: Int,
        asyncParam: Int,
        syncAction: (Feature) -> Unit,
        asyncAction: (Feature) -> Unit
    ) {

        features.values.toList().forEach { feature ->
            if (feature.loadParams and syncParam != 0) {
                tryInit(feature) {
                    syncAction(feature)
                }
            }
            if (feature.loadParams and asyncParam != 0) {
                context.coroutineScope.launch {
                    tryInit(feature) {
                        asyncAction(feature)
                    }
                }
            }
        }
    }

    private fun initializeFeatures() {
        //TODO: async called when all features are initiated ?
        measureTimeMillis {
            initFeatures(
                FeatureLoadParams.INIT_SYNC,
                FeatureLoadParams.INIT_ASYNC,
                Feature::init,
                Feature::asyncInit
            )
        }.also {
            context.log.verbose("feature manager init took $it ms")
        }
    }

    fun onActivityCreate() {
        measureTimeMillis {
            initFeatures(
                FeatureLoadParams.ACTIVITY_CREATE_SYNC,
                FeatureLoadParams.ACTIVITY_CREATE_ASYNC,
                Feature::onActivityCreate,
                Feature::asyncOnActivityCreate
            )
        }.also {
            context.log.verbose("feature manager onActivityCreate took $it ms")
        }
    }
}
