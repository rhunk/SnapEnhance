package me.rhunk.snapenhance.manager.impl

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.ConfigEnumKeys
import me.rhunk.snapenhance.features.impl.MeoPasscodeBypass
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.AntiAutoDownload
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.extras.AntiAutoSave
import me.rhunk.snapenhance.features.impl.extras.AppPasscode
import me.rhunk.snapenhance.features.impl.extras.AutoSave
import me.rhunk.snapenhance.features.impl.extras.DisableVideoLengthRestriction
import me.rhunk.snapenhance.features.impl.extras.GalleryMediaSendOverride
import me.rhunk.snapenhance.features.impl.extras.LocationSpoofer
import me.rhunk.snapenhance.features.impl.extras.MediaQualityLevelOverride
import me.rhunk.snapenhance.features.impl.extras.Notifications
import me.rhunk.snapenhance.features.impl.extras.SnapchatPlus
import me.rhunk.snapenhance.features.impl.extras.UnlimitedSnapViewTime
import me.rhunk.snapenhance.features.impl.privacy.DisableMetrics
import me.rhunk.snapenhance.features.impl.privacy.PreventMessageSending
import me.rhunk.snapenhance.features.impl.spy.AnonymousStoryViewing
import me.rhunk.snapenhance.features.impl.spy.MessageLogger
import me.rhunk.snapenhance.features.impl.spy.PreventReadReceipts
import me.rhunk.snapenhance.features.impl.spy.StealthMode
import me.rhunk.snapenhance.features.impl.ui.UITweaks
import me.rhunk.snapenhance.features.impl.ui.menus.MenuViewInjector
import me.rhunk.snapenhance.manager.Manager
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class FeatureManager(private val context: ModContext) : Manager {
    private val asyncLoadExecutorService = Executors.newCachedThreadPool()
    private val features = mutableListOf<Feature>()

    private fun register(featureClass: KClass<out Feature>) {
        runCatching {
            with(featureClass.java.newInstance()) {
                if (loadParams and FeatureLoadParams.NO_INIT != 0) return@with
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
        register(ConfigEnumKeys::class)
        register(AntiAutoDownload::class)
        register(GalleryMediaSendOverride::class)
        register(AntiAutoSave::class)
        register(UnlimitedSnapViewTime::class)
        register(DisableVideoLengthRestriction::class)
        register(MediaQualityLevelOverride::class)
        register(MeoPasscodeBypass::class)
        register(AppPasscode::class)
        register(LocationSpoofer::class)


        initializeFeatures()
    }

    private fun featureInitializer(isAsync: Boolean, param: Int, action: (Feature) -> Unit) {
        features.forEach { feature ->
            if (feature.loadParams and param == 0) return@forEach
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