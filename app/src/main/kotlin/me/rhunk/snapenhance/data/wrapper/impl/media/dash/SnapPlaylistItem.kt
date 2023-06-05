package me.rhunk.snapenhance.data.wrapper.impl.media.dash

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class SnapPlaylistItem (obj: Any?) : AbstractWrapper<Any?>(obj) {
    val snapId by lazy {
        instanceNonNull().javaClass.declaredFields.first { it.type == Long::class.javaPrimitiveType }.get(instanceNonNull()) as Long
    }
}