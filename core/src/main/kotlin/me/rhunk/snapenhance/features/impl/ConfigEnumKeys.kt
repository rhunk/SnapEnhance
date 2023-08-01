package me.rhunk.snapenhance.features.impl

import android.annotation.SuppressLint
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.getObjectField
import me.rhunk.snapenhance.util.setObjectField
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type

class ConfigEnumKeys : Feature("Config enum keys", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {

    data class HookEnumContext(
        val key: String,
        val type: Type?,
        val value: Any?,
        val set: (Any) -> Unit
    )

    companion object {
        fun hookAllEnums(enumClass: Class<*>, callback: HookEnumContext.() -> Unit) {
            //Enum(String, int, ?)
            //or Enum(?)
            val enumDataClass = enumClass.constructors[0].parameterTypes.first { clazz: Class<*> -> clazz != String::class.java && !clazz.isPrimitive }

            //get the field which contains the enum data class
            val enumDataField = enumClass.declaredFields.first { field: Field -> field.type == enumDataClass }

            val typeField = enumDataClass.declaredFields.first { field: Field -> field.type == Type::class.java }

            //get the field value of the enum data class (the first field of the class with the desc Object)
            val objectDataField = enumDataField.type.fields.first { field: Field ->
                field.type == Any::class.java && Modifier.isPublic(
                    field.modifiers
                ) && Modifier.isFinal(field.modifiers)
            }

            enumClass.enumConstants.forEach { enum ->
                enumDataField.get(enum)?.let { enumData ->
                    val key = enum.toString()
                    val type = typeField.get(enumData) as Type?
                    val value = enumData.getObjectField(objectDataField.name)
                    val set = { newValue: Any ->
                        enumData.setObjectField(objectDataField.name, newValue)
                    }
                    callback(HookEnumContext(key, type, value, set))
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    override fun onActivityCreate() {
        if (context.config.userInterface.mapFriendNameTags.get()) {
            hookAllEnums(context.mappings.getMappedClass("enums", "PLUS")) {
                if (key == "REDUCE_MY_PROFILE_UI_COMPLEXITY") set(true)
            }
        }

        hookAllEnums(context.mappings.getMappedClass("enums", "ARROYO")) {
            if (key == "ENABLE_LONG_SNAP_SENDING") {
                if (context.config.global.disableSnapSplitting.get()) set(true)
            }
        }

        if (context.config.userInterface.streakExpirationInfo.get()) {
            hookAllEnums(context.mappings.getMappedClass("enums", "FRIENDS_FEED")) {
                if (key == "STREAK_EXPIRATION_INFO") set(true)
            }
        }

        if (context.config.userInterface.blockAds.get()) {
            hookAllEnums(context.mappings.getMappedClass("enums", "SNAPADS")) {
                if (key == "BYPASS_AD_FEATURE_GATE") {
                    set(true)
                }
                if (key == "CUSTOM_AD_SERVER_URL" || key == "CUSTOM_AD_INIT_SERVER_URL" || key == "CUSTOM_AD_TRACKER_URL") {
                    set("http://127.0.0.1")
                }
            }
        }

        context.config.userInterface.storyViewerOverride.getNullable()?.let { value ->
            hookAllEnums(context.mappings.getMappedClass("enums", "DISCOVER_FEED")) {
                if (key == "DF_ENABLE_SHOWS_PAGE_CONTROLS" && value == "DISCOVER_PLAYBACK_SEEKBAR") {
                    set(true)
                }
                if (key == "DF_VOPERA_FOR_STORIES" && value == "VERTICAL_STORY_VIEWER") {
                    set(true)
                }
            }
        }

        val sharedPreferencesImpl = context.androidContext.classLoader.loadClass("android.app.SharedPreferencesImpl")

        sharedPreferencesImpl.methods.first { it.name == "getBoolean" }.hook(HookStage.BEFORE) { param ->
            when (param.arg<String>(0)) {
                "SIG_APP_APPEARANCE_SETTING" -> if (context.config.userInterface.enableAppAppearance.get()) param.setResult(true)
                "SPOTLIGHT_5TH_TAB_ENABLED" -> if (context.config.userInterface.disableSpotlight.get()) param.setResult(false)
            }
        }
    }
}