package me.rhunk.snapenhance.features.impl

import android.annotation.SuppressLint
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.setObjectField
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class ConfigEnumKeys : Feature("Config enum keys", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private fun hookAllEnums(enumClass: Class<*>, callback: (String, (Any) -> Unit) -> Unit) {
        //Enum(String, int, ?)
        //or Enum(?)
        val enumDataClass = enumClass.constructors[0].parameterTypes.first { clazz: Class<*> -> clazz != String::class.java && !clazz.isPrimitive }

        //get the field which contains the enum data class
        val enumDataField = enumClass.declaredFields.first { field: Field -> field.type == enumDataClass }

        //get the field value of the enum data class (the first field of the class with the desc Object)
        val objectDataField = enumDataField.type.fields.first { field: Field ->
            field.type == Any::class.java && Modifier.isPublic(
                field.modifiers
            ) && Modifier.isFinal(field.modifiers)
        }

        enumClass.enumConstants.forEach { enum ->
            enumDataField.get(enum)?.let { enumData ->
                val key = enum.toString()
                callback(key) { newValue ->
                    enumData.setObjectField(objectDataField.name, newValue)
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    override fun onActivityCreate() {
        if (context.config.bool(ConfigProperty.NEW_MAP_UI)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "PLUS")) { key, set ->
                if (key == "REDUCE_MY_PROFILE_UI_COMPLEXITY") set(true)
            }
        }

        hookAllEnums(context.mappings.getMappedClass("enums", "ARROYO")) { key, set ->
            if (key == "ENABLE_LONG_SNAP_SENDING") {
                if (context.config.bool(ConfigProperty.DISABLE_SNAP_SPLITTING)) set(true)
            }
        }

        if (context.config.bool(ConfigProperty.STREAK_EXPIRATION_INFO)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "FRIENDS_FEED")) { key, set ->
                if (key == "STREAK_EXPIRATION_INFO") set(true)
            }
        }

        if (context.config.bool(ConfigProperty.BLOCK_ADS)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "SNAPADS")) { key, set ->
                if (key == "BYPASS_AD_FEATURE_GATE") {
                    set(true)
                }
                if (key == "CUSTOM_AD_SERVER_URL" || key == "CUSTOM_AD_INIT_SERVER_URL" || key == "CUSTOM_AD_TRACKER_URL") {
                    set("http://127.0.0.1")
                }
            }
        }

        ConfigProperty.ENABLE_APP_APPEARANCE.valueContainer.addPropertyChangeListener {
            context.softRestartApp(true)
        }

        val sharedPreferencesImpl = context.androidContext.classLoader.loadClass("android.app.SharedPreferencesImpl")

        sharedPreferencesImpl.methods.first { it.name == "getBoolean" }.hook(HookStage.BEFORE) { param ->
            when (param.arg<String>(0)) {
                "SIG_APP_APPEARANCE_SETTING" -> if (context.config.bool(ConfigProperty.ENABLE_APP_APPEARANCE)) param.setResult(true)
                "SPOTLIGHT_5TH_TAB_ENABLED" -> if (context.config.bool(ConfigProperty.DISABLE_SPOTLIGHT)) param.setResult(false)
            }
        }
    }
}