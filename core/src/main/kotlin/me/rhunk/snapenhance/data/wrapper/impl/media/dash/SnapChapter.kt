package me.rhunk.snapenhance.data.wrapper.impl.media.dash

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class SnapChapter (obj: Any?) : AbstractWrapper(obj) {
    val snapId by lazy {
        instanceNonNull().javaClass.declaredFields.first { it.type == Long::class.javaPrimitiveType }.get(instanceNonNull()) as Long
    }
    val startTimeMs by lazy {
        instanceNonNull().javaClass.declaredFields.filter { it.type == Long::class.javaPrimitiveType }[1].get(instanceNonNull()) as Long
    }
}