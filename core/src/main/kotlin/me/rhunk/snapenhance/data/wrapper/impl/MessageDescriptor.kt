package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ktx.getObjectField

class MessageDescriptor(obj: Any?) : AbstractWrapper(obj) {
    val messageId: Long get() = instanceNonNull().getObjectField("mMessageId") as Long
    val conversationId: SnapUUID get() = SnapUUID(instanceNonNull().getObjectField("mConversationId")!!)
}