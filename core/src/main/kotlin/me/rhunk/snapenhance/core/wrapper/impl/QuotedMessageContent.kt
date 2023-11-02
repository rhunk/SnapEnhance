package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class QuotedMessageContent(obj: Any?) : AbstractWrapper(obj) {
    var messageId get() = instanceNonNull().getObjectField("mMessageId") as Long
        set(value) = instanceNonNull().setObjectField("mMessageId", value)
}