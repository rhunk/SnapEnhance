package me.rhunk.snapenhance.features.impl

import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.ktx.setObjectField

class ConfigurationOverride : Feature("Configuration Override", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val propertyOverrides = mutableMapOf<String, Pair<(() -> Boolean), Any>>()

        fun overrideProperty(key: String, filter: () -> Boolean, value: Any) {
            propertyOverrides[key] = Pair(filter, value)
        }

        overrideProperty("STREAK_EXPIRATION_INFO", { context.config.userInterface.streakExpirationInfo.get() }, true)

        overrideProperty("FORCE_CAMERA_HIGHEST_FPS", { context.config.camera.forceHighestFrameRate.get() }, true)
        overrideProperty("MEDIA_RECORDER_MAX_QUALITY_LEVEL", { context.config.camera.forceCameraSourceEncoding.get() }, true)
        overrideProperty("REDUCE_MY_PROFILE_UI_COMPLEXITY", { context.config.userInterface.mapFriendNameTags.get() }, true)
        overrideProperty("ENABLE_LONG_SNAP_SENDING", { context.config.global.disableSnapSplitting.get() }, true)

        context.config.userInterface.storyViewerOverride.getNullable()?.let { state ->
            overrideProperty("DF_ENABLE_SHOWS_PAGE_CONTROLS", { state == "DISCOVER_PLAYBACK_SEEKBAR" }, true)
            overrideProperty("DF_VOPERA_FOR_STORIES", { state == "VERTICAL_STORY_VIEWER" }, true)
        }

        overrideProperty("SIG_APP_APPEARANCE_SETTING", { context.config.userInterface.enableAppAppearance.get() }, true)
        overrideProperty("SPOTLIGHT_5TH_TAB_ENABLED", { context.config.userInterface.disableSpotlight.get() }, false)

        overrideProperty("BYPASS_AD_FEATURE_GATE", { context.config.global.blockAds.get() }, true)
        arrayOf("CUSTOM_AD_TRACKER_URL", "CUSTOM_AD_INIT_SERVER_URL", "CUSTOM_AD_SERVER_URL").forEach {
            overrideProperty(it, { context.config.global.blockAds.get() }, "http://127.0.0.1")
        }

        val compositeConfigurationProviderMappings = context.mappings.getMappedMap("CompositeConfigurationProvider")
        val enumMappings = compositeConfigurationProviderMappings["enum"] as Map<*, *>

        findClass(compositeConfigurationProviderMappings["class"].toString()).hook(
            compositeConfigurationProviderMappings["observeProperty"].toString(),
            HookStage.BEFORE
        ) { param ->
            val enumData = param.arg<Any>(0)
            val key = enumData.toString()
            val setValue: (Any?) -> Unit = { value ->
                val valueHolder = XposedHelpers.callMethod(enumData, enumMappings["getValue"].toString())
                valueHolder.setObjectField(enumMappings["defaultValueField"].toString(), value)
            }

            propertyOverrides[key]?.let { (filter, value) ->
                if (!filter()) return@let
                setValue(value)
            }
        }

        findClass(compositeConfigurationProviderMappings["class"].toString()).hook(
            compositeConfigurationProviderMappings["getProperty"].toString(),
            HookStage.AFTER
        ) { param ->
            val propertyKey = param.arg<Any>(0).toString()

            propertyOverrides[propertyKey]?.let { (filter, value) ->
                if (!filter()) return@let
                param.setResult(value)
            }
        }

        arrayOf("getBoolean", "getInt", "getLong", "getFloat", "getString").forEach { methodName ->
            findClass("android.app.SharedPreferencesImpl").hook(
                methodName,
                HookStage.BEFORE
            ) { param ->
                val key = param.argNullable<Any>(0).toString()
                propertyOverrides[key]?.let { (filter, value) ->
                    if (!filter()) return@let
                    param.setResult(value)
                }
            }
        }
    }
}