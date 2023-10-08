package me.rhunk.snapenhance.manager.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.core.Logger
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.features.impl.ConfigurationOverride
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.ScopeSync
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.downloader.ProfilePictureDownloader
import me.rhunk.snapenhance.features.impl.experiments.*
import me.rhunk.snapenhance.features.impl.privacy.DisableMetrics
import me.rhunk.snapenhance.features.impl.privacy.PreventMessageSending
import me.rhunk.snapenhance.features.impl.spying.AnonymousStoryViewing
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.features.impl.spying.PreventReadReceipts
import me.rhunk.snapenhance.features.impl.spying.SnapToChatMedia
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.features.impl.tweaks.*
import me.rhunk.snapenhance.features.impl.ui.ClientBootstrapOverride
import me.rhunk.snapenhance.features.impl.ui.FriendFeedMessagePreview
import me.rhunk.snapenhance.features.impl.ui.PinConversations
import me.rhunk.snapenhance.features.impl.ui.UITweaks
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.ui.menu.impl.MenuViewInjector
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
                        Logger.xposedLog("Failed to register feature ${clazz.simpleName}", it)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Feature> get(featureClass: KClass<T>): T? {
        return features.find { it::class == featureClass } as? T
    }

    fun getRuleFeatures() = features.filterIsInstance<MessagingRuleFeature>()

    override fun init() {
        register(
            EndToEndEncryption::class,
            ScopeSync::class,
            Messaging::class,
            MediaDownloader::class,
            StealthMode::class,
            MenuViewInjector::class,
            PreventReadReceipts::class,
            AnonymousStoryViewing::class,
            MessageLogger::class,
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
            SnapToChatMedia::class,
            FriendFeedMessagePreview::class,
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