package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class QuotedMessage(obj: Any?) : AbstractWrapper(obj) {
    val content get() = QuotedMessageContent(instanceNonNull().getObjectField("mContent"))
}