package me.rhunk.snapenhance.manager.impl

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.AutoUpdater
import me.rhunk.snapenhance.features.impl.ConfigurationOverride
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.downloader.ProfilePictureDownloader
import me.rhunk.snapenhance.features.impl.experiments.AmoledDarkMode
import me.rhunk.snapenhance.features.impl.experiments.AppPasscode
import me.rhunk.snapenhance.features.impl.experiments.DeviceSpooferHook
import me.rhunk.snapenhance.features.impl.experiments.InfiniteStoryBoost
import me.rhunk.snapenhance.features.impl.experiments.MeoPasscodeBypass
import me.rhunk.snapenhance.features.impl.experiments.NoFriendScoreDelay
import me.rhunk.snapenhance.features.impl.experiments.UnlimitedMultiSnap
import me.rhunk.snapenhance.features.impl.privacy.DisableMetrics
import me.rhunk.snapenhance.features.impl.privacy.PreventMessageSending
import me.rhunk.snapenhance.features.impl.spying.AnonymousStoryViewing
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.features.impl.spying.PreventReadReceipts
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.features.impl.tweaks.AutoSave
import me.rhunk.snapenhance.features.impl.tweaks.CameraTweaks
import me.rhunk.snapenhance.features.impl.tweaks.DisableVideoLengthRestriction
import me.rhunk.snapenhance.features.impl.tweaks.GalleryMediaSendOverride
import me.rhunk.snapenhance.features.impl.tweaks.GooglePlayServicesDialogs
import me.rhunk.snapenhance.features.impl.tweaks.LocationSpoofer
import me.rhunk.snapenhance.features.impl.tweaks.MediaQualityLevelOverride
import me.rhunk.snapenhance.features.impl.tweaks.Notifications
import me.rhunk.snapenhance.features.impl.tweaks.SnapchatPlus
import me.rhunk.snapenhance.features.impl.tweaks.UnlimitedSnapViewTime
import me.rhunk.snapenhance.features.impl.ui.PinConversations
import me.rhunk.snapenhance.features.impl.ui.StartupPageOverride
import me.rhunk.snapenhance.features.impl.ui.UITweaks
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.ui.menu.impl.MenuViewInjector
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class FeatureManager(private val context: ModContext) : Manager {
    private val asyncLoadExecutorService = Executors.newFixedThreadPool(5)
    private val features = mutableListOf<Feature>()

    private fun register(featureClass: KClass<out Feature>) {
        runCatching {
            with(featureClass.java.newInstance()) {
                context = this@FeatureManager.context
                features.add(this)
            }
        }.onFailure {
            Logger.xposedLog("Failed to register feature ${featureClass.simpleName}", it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Feature> get(featureClass: KClass<T>): T? {
        return features.find { it::class == featureClass } as? T
    }

    override fun init() {
        register(Messaging::class)
        register(MediaDownloader::class)
        register(StealthMode::class)
        register(MenuViewInjector::class)
        register(PreventReadReceipts::class)
        register(AnonymousStoryViewing::class)
        register(MessageLogger::class)
        register(SnapchatPlus::class)
        register(DisableMetrics::class)
        register(PreventMessageSending::class)
        register(Notifications::class)
        register(AutoSave::class)
        register(UITweaks::class)
        register(ConfigurationOverride::class)
        register(GalleryMediaSendOverride::class)
        register(UnlimitedSnapViewTime::class)
        register(DisableVideoLengthRestriction::class)
        register(MediaQualityLevelOverride::class)
        register(MeoPasscodeBypass::class)
        register(AppPasscode::class)
        register(LocationSpoofer::class)
        register(AutoUpdater::class)
        register(CameraTweaks::class)
        register(InfiniteStoryBoost::class)
        register(AmoledDarkMode::class)
        register(PinConversations::class)
        register(UnlimitedMultiSnap::class)
        register(DeviceSpooferHook::class)
        register(StartupPageOverride::class)
        register(GooglePlayServicesDialogs::class)
        register(NoFriendScoreDelay::class)
        register(ProfilePictureDownloader::class)

        initializeFeatures()
    }

    private fun featureInitializer(isAsync: Boolean, param: Int, action: (Feature) -> Unit) {
        features.filter { it.loadParams and param != 0 }.forEach { feature ->
            val callback = {
                runCatching {
                    action(feature)
                }.onFailure {
                    Logger.xposedLog("Failed to init feature ${feature.nameKey}", it)
                    context.longToast("Failed to init feature ${feature.nameKey}")
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