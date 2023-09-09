package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class MessageDescriptor(obj: Any?) : AbstractWrapper(obj) {
    val messageId: Long get() = instanceNonNull().getObjectField("mMessageId") as Long
    val conversationId: SnapUUID get() = SnapUUID(instanceNonNull().getObjectField("mConversationId")!!)
}