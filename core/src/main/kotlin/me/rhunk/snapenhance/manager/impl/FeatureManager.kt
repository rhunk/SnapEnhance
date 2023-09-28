package me.rhunk.snapenhance.manager.impl

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
import me.rhunk.snapenhance.features.impl.ui.PinConversations
import me.rhunk.snapenhance.features.impl.ui.UITweaks
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.ui.menu.impl.MenuViewInjector
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class FeatureManager(private val context: ModContext) : Manager {
    private val asyncLoadExecutorService = Executors.newFixedThreadPool(5)
    private val features = mutableListOf<Feature>()

    private fun register(vararg featureClasses: KClass<out Feature>) {
        featureClasses.forEach { clazz ->
            runCatching {
                clazz.constructors.first().call().also { feature ->
                    feature.context = context
                    features.add(feature)
                }
            }.onFailure {
                Logger.xposedLog("Failed to register feature ${clazz.simpleName}", it)
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
            DisableVideoLengthRestriction::class,
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
        )

        initializeFeatures()
    }

    private fun featureInitializer(isAsync: Boolean, param: Int, action: (Feature) -> Unit) {
        features.filter { it.loadParams and param != 0 }.forEach { feature ->
            val callback = {
                runCatching {
                    action(feature)
                }.onFailure {
                    context.log.error("Failed to init feature ${feature.featureKey}", it)
                    context.longToast("Failed to load feature ${feature.featureKey}! Check logcat for more details.")
                }
            }
            if (!isAsync) {
                callback()
                return@forEach
            }
            asyncLoadExecutorService.submit {
                callback()
            }
        }
    }

    private fun initializeFeatures() {
        //TODO: async called when all features are initiated ?
        featureInitializer(false, FeatureLoadParams.INIT_SYNC) { it.init() }
        featureInitializer(true, FeatureLoadParams.INIT_ASYNC) { it.asyncInit() }
    }

    override fun onActivityCreate() {
        featureInitializer(false, FeatureLoadParams.ACTIVITY_CREATE_SYNC) { it.onActivityCreate() }
        featureInitializer(true, FeatureLoadParams.ACTIVITY_CREATE_ASYNC) { it.asyncOnActivityCreate() }
    }
}