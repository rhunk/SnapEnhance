package me.rhunk.snapenhance.core.manager.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.features.impl.ConfigurationOverride
import me.rhunk.snapenhance.core.features.impl.MixerStories
import me.rhunk.snapenhance.core.features.impl.OperaViewerParamsOverride
import me.rhunk.snapenhance.core.features.impl.ScopeSync
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.ProfilePictureDownloader
import me.rhunk.snapenhance.core.features.impl.experiments.*
import me.rhunk.snapenhance.core.features.impl.global.*
import me.rhunk.snapenhance.core.features.impl.messaging.*
import me.rhunk.snapenhance.core.features.impl.spying.*
import me.rhunk.snapenhance.core.features.impl.tweaks.BypassScreenshotDetection
import me.rhunk.snapenhance.core.features.impl.tweaks.CameraTweaks
import me.rhunk.snapenhance.core.features.impl.tweaks.PreventMessageListAutoScroll
import me.rhunk.snapenhance.core.features.impl.tweaks.UnsaveableMessages
import me.rhunk.snapenhance.core.features.impl.ui.*
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.manager.Manager
import me.rhunk.snapenhance.core.ui.menu.MenuViewInjector
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

class FeatureManager(
    private val context: ModContext
) : Manager {
    private val features = mutableMapOf<KClass<out Feature>, Feature>()

    private fun register(vararg featureList: Feature) {
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

    override fun init() {
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
            UnsaveableMessages(),
            SendOverride(),
            UnlimitedSnapViewTime(),
            BypassVideoLengthRestriction(),
            MediaQualityLevelOverride(),
            MeoPasscodeBypass(),
            AppPasscode(),
            LocationSpoofer(),
            CameraTweaks(),
            InfiniteStoryBoost(),
            AmoledDarkMode(),
            PinConversations(),
            UnlimitedMultiSnap(),
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
            FideliusIndicator(),
            EditTextOverride(),
            PreventForcedLogout(),
            SuspendLocationUpdates(),
            ConversationToolbox(),
            SpotlightCommentsUsername(),
            OperaViewerParamsOverride(),
            StealthModeIndicator(),
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

    override fun onActivityCreate() {
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