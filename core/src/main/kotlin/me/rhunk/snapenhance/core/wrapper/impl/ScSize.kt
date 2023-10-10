package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class ScSize(
    obj: Any?
) : AbstractWrapper(obj) {
    private val firstField by lazy {
        instanceNonNull().javaClass.declaredFields.first { it.type == Int::class.javaPrimitiveType }.also { it.isAccessible = true }
    }

    private val secondField by lazy {
        instanceNonNull().javaClass.declaredFields.last { it.type == Int::class.javaPrimitiveType }.also { it.isAccessible = true }
    }


    var first: Int get() = firstField.getInt(instanceNonNull())
        set(value) {
            firstField.setInt(instanceNonNull(), value)
        }

    var second: Int get() = secondField.getInt(instanceNonNull())
        set(value) {
            secondField.setInt(instanceNonNull(), value)
        }
}