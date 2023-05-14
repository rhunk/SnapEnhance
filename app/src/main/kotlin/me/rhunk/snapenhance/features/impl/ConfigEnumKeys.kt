package me.rhunk.snapenhance.features.impl

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.util.setObjectField
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

class ConfigEnumKeys : Feature("Config enum keys", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private fun hookAllEnums(enumClass: Class<*>, callback: (String, AtomicReference<Any>) -> Unit) {
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
                val key = objectDataField.get(enumData)!!.toString()
                val value = AtomicReference(objectDataField.get(enumData))
                callback(key, value)
                enumData.setObjectField(objectDataField.name, value.get())
            }
        }
    }

    override fun onActivityCreate() {
        if (context.config.bool(ConfigProperty.NEW_MAP_UI)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "PLUS")) { key, atomicValue ->
                if (key == "REDUCE_MY_PROFILE_UI_COMPLEXITY") atomicValue.set(true)
            }
        }

        if (context.config.bool(ConfigProperty.LONG_SNAP_SENDING)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "ARROYO")) { key, atomicValue ->
                if (key == "ENABLE_LONG_SNAP_SENDING") atomicValue.set(true)
            }
        }

        if (context.config.bool(ConfigProperty.STREAKEXPIRATIONINFO)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "FRIENDS_FEED")) { key, atomicValue ->
                if (key == "STREAK_EXPIRATION_INFO") atomicValue.set(true)
            }
        }

        if (context.config.bool(ConfigProperty.BLOCK_ADS)) {
            hookAllEnums(context.mappings.getMappedClass("enums", "SNAPADS")) { key, atomicValue ->
                if (key == "BYPASS_AD_FEATURE_GATE") {
                    atomicValue.set(true)
                }
                if (key == "CUSTOM_AD_SERVER_URL" || key == "CUSTOM_AD_INIT_SERVER_URL" || key == "CUSTOM_AD_TRACKER_URL") {
                    atomicValue.set("http://127.0.0.1")
                }
            }
        }
    }
}