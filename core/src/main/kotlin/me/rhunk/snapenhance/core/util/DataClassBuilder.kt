package me.rhunk.snapenhance.core.util


inline fun Any?.dataBuilder(dataClassBuilder: DataClassBuilder.() -> Unit): Any? {
    return DataClassBuilder(
        when (this) {
            is Class<*> -> CallbackBuilder.createEmptyObject(
                this.constructors.firstOrNull() ?: return null
            ) ?: return null
            else -> this
        } ?: return null
    ).apply(dataClassBuilder).build()
}

// Util for building/editing data classes
class DataClassBuilder(
    private val instance: Any,
) {
    fun set(fieldName: String, value: Any?) {
        val field = instance::class.java.declaredFields.firstOrNull { it.name == fieldName } ?: return
        val fieldType = field.type
        field.isAccessible = true

        when {
            fieldType.isEnum -> {
                val enumValue = fieldType.enumConstants.firstOrNull { it.toString() == value } ?: return
                field.set(instance, enumValue)
            }
            fieldType.isPrimitive -> {
                when (fieldType) {
                    Boolean::class.javaPrimitiveType -> field.setBoolean(instance, value as Boolean)
                    Byte::class.javaPrimitiveType -> field.setByte(instance, value as Byte)
                    Char::class.javaPrimitiveType -> field.setChar(instance, value as Char)
                    Short::class.javaPrimitiveType -> field.setShort(instance, value as Short)
                    Int::class.javaPrimitiveType -> field.setInt(instance, value as Int)
                    Long::class.javaPrimitiveType -> field.setLong(instance, value as Long)
                    Float::class.javaPrimitiveType -> field.setFloat(instance, value as Float)
                    Double::class.javaPrimitiveType -> field.setDouble(instance, value as Double)
                }
            }
            else -> field.set(instance, value)
        }
    }

    fun set(vararg fields: Pair<String, Any?>) = fields.forEach { set(it.first, it.second) }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(fieldName: String): T? {
        val field = instance::class.java.declaredFields.firstOrNull { it.name == fieldName } ?: return null
        field.isAccessible = true
        return field.get(instance) as? T
    }

    fun from(fieldName: String, new: Boolean = false, callback: DataClassBuilder.() -> Unit) {
        val field = instance::class.java.declaredFields.firstOrNull { it.name == fieldName } ?: return
        field.isAccessible = true

        val lazyInstance by lazy { CallbackBuilder.createEmptyObject(field.type.constructors.firstOrNull() ?: return@lazy null) ?: return@lazy null }
        val builderInstance = if (new) lazyInstance else {
            field.get(instance).takeIf { it != null } ?: lazyInstance
        }

        DataClassBuilder(builderInstance ?: return).apply(callback)

        field.set(instance, builderInstance)
    }

    fun <T> cast(type: Class<T>, callback: T.() -> Unit) {
        type.cast(instance)?.let { callback(it) }
    }

    fun build() = instance
}