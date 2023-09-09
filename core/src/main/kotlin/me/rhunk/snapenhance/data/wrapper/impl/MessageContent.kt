package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class MessageContent(obj: Any?) : AbstractWrapper(obj) {
    var content
        get() = instanceNonNull().getObjectField("mContent") as ByteArray
        set(value) = instanceNonNull().setObjectField("mContent", value)
    var contentType by enum("mContentType", ContentType.UNKNOWN)
}