package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.Logger.debug
import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Objects


class EnumMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        val enumMappings = HashMap<String, String>()
        var enumQualityLevel: Class<*>? = null

        //settings classes have an interface that extends Serializable and contains the getName method
        //this enum classes are used to store the settings values
        //Setting enum class -> implements an interface -> getName method
        classes.forEach { clazz ->
            if (!clazz.isEnum) return@forEach

            //quality level enum
            if (enumQualityLevel == null) {
                if (clazz.enumConstants.any { it.toString().startsWith("LEVEL_NONE") }) {
                    enumMappings["QualityLevel"] = clazz.name
                    enumQualityLevel = clazz
                }
            }

            if (clazz.interfaces.isEmpty()) return@forEach
            val serializableInterfaceClass = clazz.interfaces[0]
            if (serializableInterfaceClass.methods
                    .filter { method: Method -> method.declaringClass == serializableInterfaceClass }
                    .none { method: Method -> method.name == "getName" }
            ) return@forEach

            runCatching {
                val getEnumNameMethod =
                    serializableInterfaceClass.methods.first { it!!.returnType.isEnum }
                clazz.enumConstants?.onEach { enumConstant ->
                    val enumName =
                        Objects.requireNonNull(getEnumNameMethod.invoke(enumConstant)).toString()
                    enumMappings[enumName] = clazz.name
                }
            }
        }

        //find the media quality level provider
        for (clazz in classes) {
            if (!Modifier.isAbstract(clazz.modifiers)) continue
            if (clazz.fields.none { Modifier.isTransient(it.modifiers) }) continue
            clazz.methods.firstOrNull { it.returnType == enumQualityLevel }?.let {
                mappings["MediaQualityLevelProvider"] = clazz.name
                mappings["MediaQualityLevelProviderMethod"] = it.name
                Logger.debug("found MediaQualityLevelProvider: ${clazz.name}.${it.name}")
            }
        }

        debug("found " + enumMappings.size + " enums")
        mappings["enums"] = enumMappings
    }
}