package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class MessageContent(obj: Any?) : AbstractWrapper(obj) {
    var content
        get() = instanceNonNull().getObjectField("mContent") as ByteArray
        set(value) = instanceNonNull().setObjectField("mContent", value)
    val quotedMessage
        get() = QuotedMessage(instanceNonNull().getObjectField("mQuotedMessage"))
    var contentType by enum("mContentType", ContentType.UNKNOWN)
}