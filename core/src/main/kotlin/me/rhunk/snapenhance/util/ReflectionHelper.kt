package me.rhunk.snapenhance.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Objects

object ReflectionHelper {
    /**
     * Searches for a field with a class that has a method with the specified name
     */
    fun searchFieldWithClassMethod(clazz: Class<*>, methodName: String): Field? {
        return clazz.declaredFields.firstOrNull { f: Field? ->
            try {
                return@firstOrNull Arrays.stream(
                    f!!.type.declaredMethods
                ).anyMatch { method: Method -> method.name == methodName }
            } catch (e: Exception) {
                return@firstOrNull false
            }
        }
    }

    fun searchFieldByType(clazz: Class<*>, type: Class<*>): Field? {
        return clazz.declaredFields.firstOrNull { f: Field? -> f!!.type == type }
    }

    fun searchFieldTypeInSuperClasses(clazz: Class<*>, type: Class<*>): Field? {
        val field = searchFieldByType(clazz, type)
        if (field != null) {
            return field
        }
        val superclass = clazz.superclass
        return superclass?.let { searchFieldTypeInSuperClasses(it, type) }
    }

    fun searchFieldStartsWithToString(
        clazz: Class<*>,
        instance: Any,
        toString: String?
    ): Field? {
        return clazz.declaredFields.firstOrNull { f: Field ->
            try {
                f.isAccessible = true
                return@firstOrNull Objects.requireNonNull(f[instance]).toString()
                    .startsWith(
                        toString!!
                    )
            } catch (e: Throwable) {
                return@firstOrNull false
            }
        }
    }


    fun searchFieldContainsToString(
        clazz: Class<*>,
        instance: Any?,
        toString: String?
    ): Field? {
        return clazz.declaredFields.firstOrNull { f: Field ->
            try {
                f.isAccessible = true
                return@firstOrNull Objects.requireNonNull(f[instance]).toString()
                    .contains(toString!!)
            } catch (e: Throwable) {
                return@firstOrNull false
            }
        }
    }

    fun searchFirstFieldTypeInClassRecursive(clazz: Class<*>, type: Class<*>): Field? {
        return clazz.declaredFields.firstOrNull {
            val field = searchFieldByType(it.type, type)
            return@firstOrNull field != null
        }
    }

    /**
     * Searches for a field with a class that has a method with the specified return type
     */
    fun searchMethodWithReturnType(clazz: Class<*>, returnType: Class<*>): Method? {
        return clazz.declaredMethods.first { m: Method -> m.returnType == returnType }
    }

    /**
     * Searches for a field with a class that has a method with the specified return type and parameter types
     */
    fun searchMethodWithParameterAndReturnType(
        aClass: Class<*>,
        returnType: Class<*>,
        vararg parameters: Class<*>
    ): Method? {
        return aClass.declaredMethods.firstOrNull { m: Method ->
            if (m.returnType != returnType) {
                return@firstOrNull false
            }
            val parameterTypes = m.parameterTypes
            if (parameterTypes.size != parameters.size) {
                return@firstOrNull false
            }
            for (i in parameterTypes.indices) {
                if (parameterTypes[i] != parameters[i]) {
                    return@firstOrNull false
                }
            }
            true
        }
    }

    fun getDeclaredFieldsRecursively(clazz: Class<*>): List<Field> {
        val fields = clazz.declaredFields.toMutableList()
        val superclass = clazz.superclass
        if (superclass != null) {
            fields.addAll(getDeclaredFieldsRecursively(superclass))
        }
        return fields
    }
}