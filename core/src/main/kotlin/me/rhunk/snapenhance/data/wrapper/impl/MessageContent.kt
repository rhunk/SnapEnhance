package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField
import me.rhunk.snapenhance.util.setObjectField

class MessageContent(obj: Any?) : AbstractWrapper(obj) {
    var content
        get() = instanceNonNull().getObjectField("mContent") as ByteArray
        set(value) = instanceNonNull().setObjectField("mContent", value)
    var contentType
        get() = getEnumValue("mContentType", ContentType.UNKNOWN)
        set(value) = setEnumValue("mContentType", value)
}