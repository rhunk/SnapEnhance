package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.hasStaticConstructorString
import me.rhunk.snapenhance.mapper.ext.isAbstract
import me.rhunk.snapenhance.mapper.ext.isEnum
import com.android.tools.smali.dexlib2.AccessFlags

class MediaQualityLevelProviderMapper : AbstractClassMapper("MediaQualityLevelProvider") {
    val mediaQualityLevelProvider = classReference("mediaQualityLevelProvider")
    val mediaQualityLevelProviderMethod = string("mediaQualityLevelProviderMethod")

    init {
        var enumQualityLevel : String? = null

        mapper {
            for (enumClass in classes) {
                if (!enumClass.isEnum()) continue

                if (enumClass.hasStaticConstructorString("LEVEL_MAX")) {
                    enumQualityLevel = enumClass.getClassName()
                    break;
                }
            }
        }

        mapper {
            if (enumQualityLevel == null) return@mapper

            for (clazz in classes) {
                if (!clazz.isAbstract()) continue
                if (clazz.fields.none { it.accessFlags and AccessFlags.TRANSIENT.value != 0 }) continue

                clazz.methods.firstOrNull { it.returnType == "L$enumQualityLevel;" }?.let {
                    mediaQualityLevelProvider.set(clazz.getClassName())
                    mediaQualityLevelProviderMethod.set(it.name)
                    return@mapper
                }
            }
        }
    }
}
