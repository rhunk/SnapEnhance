package me.rhunk.snapenhance.core.manager.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.features.impl.ConfigurationOverride
import me.rhunk.snapenhance.core.features.impl.ScopeSync
import me.rhunk.snapenhance.core.features.impl.Stories
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.ProfilePictureDownloader
import me.rhunk.snapenhance.core.features.impl.experiments.*
import me.rhunk.snapenhance.core.features.impl.global.*
import me.rhunk.snapenhance.core.features.impl.messaging.*
import me.rhunk.snapenhance.core.features.impl.spying.HalfSwipeNotifier
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.features.impl.tweaks.BypassScreenshotDetection
import me.rhunk.snapenhance.core.features.impl.tweaks.CameraTweaks
import me.rhunk.snapenhance.core.features.impl.tweaks.PreventMessageListAutoScroll
import me.rhunk.snapenhance.core.features.impl.ui.*
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.manager.Manager
import me.rhunk.snapenhance.core.ui.menu.impl.MenuViewInjector
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

class FeatureManager(
    private val context: ModContext
) : Manager {
    private val features = mutableListOf<Feature>()

    private fun register(vararg featureClasses: KClass<out Feature>) {
        runBlocking {
            featureClasses.forEach { clazz ->
                launch(Dispatchers.IO) {
                    runCatching {
                        clazz.java.constructors.first().newInstance()
                            .let { it as Feature }
                            .also {
                                it.context = context
                                synchronized(features) {
                                    features.add(it)
                                }
                            }
                    }.onFailure {
                        CoreLogger.xposedLog("Failed to register feature ${clazz.simpleName}", it)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Feature> get(featureClass: KClass<T>): T? {
        return features.find { it::class == featureClass } as? T
    }

    fun getRuleFeatures() = features.filterIsInstance<MessagingRuleFeature>().sortedBy { it.ruleType.ordinal }

    override fun init() {
        register(
            EndToEndEncryption::class,
            ScopeSync::class,
            PreventMessageListAutoScroll::class,
            Messaging::class,
            MediaDownloader::class,
            StealthMode::class,
            MenuViewInjector::class,
            PreventReadReceipts::class,
            MessageLogger::class,
            ConvertMessageLocally::class,
            SnapchatPlus::class,
            DisableMetrics::class,
            PreventMessageSending::class,
            Notifications::class,
            AutoSave::class,
            UITweaks::class,
            ConfigurationOverride::class,
            SendOverride::class,
            UnlimitedSnapViewTime::class,
            BypassVideoLengthRestriction::class,
            MediaQualityLevelOverride::class,
            MeoPasscodeBypass::class,
            AppPasscode::class,
            LocationSpoofer::class,
            CameraTweaks::class,
            InfiniteStoryBoost::class,
            AmoledDarkMode::class,
            PinConversations::class,
            UnlimitedMultiSnap::class,
            DeviceSpooferHook::class,
            ClientBootstrapOverride::class,
            GooglePlayServicesDialogs::class,
            NoFriendScoreDelay::class,
            ProfilePictureDownloader::class,
            AddFriendSourceSpoof::class,
            DisableReplayInFF::class,
            OldBitmojiSelfie::class,
            FriendFeedMessagePreview::class,
            HideStreakRestore::class,
            HideFriendFeedEntry::class,
            HideQuickAddFriendFeed::class,
            CallStartConfirmation::class,
            SnapPreview::class,
            InstantDelete::class,
            BypassScreenshotDetection::class,
            HalfSwipeNotifier::class,
            DisableConfirmationDialogs::class,
            Stories::class,
            DisableComposerModules::class,
            FideliusIndicator::class,
            EditTextOverride::class,
        )

        initializeFeatures()
    }

    private fun initFeatures(
        syncParam: Int,
        asyncParam: Int,
        syncAction: (Feature) -> Unit,
        asyncAction: (Feature) -> Unit
    ) {
        fun tryInit(feature: Feature, block: () -> Unit) {
            runCatching {
                block()
            }.onFailure {
                context.log.error("Failed to init feature ${feature.featureKey}", it)
                context.longToast("Failed to init feature ${feature.featureKey}! Check logcat for more details.")
            }
        }

        features.toList().forEach { feature ->
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