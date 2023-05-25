package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.Logger.debug
import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Method
import java.util.Objects


class EnumMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        val enumMappings = HashMap<String, String>()
        //settings classes have an interface that extends Serializable and contains the getName method
        //this enum classes are used to store the settings values
        //Setting enum class -> implements an interface -> getName method
        classes.forEach { clazz ->
            if (!clazz.isEnum) return@forEach
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
        debug("found " + enumMappings.size + " enums")
        mappings["enums"] = enumMappings
    }
}