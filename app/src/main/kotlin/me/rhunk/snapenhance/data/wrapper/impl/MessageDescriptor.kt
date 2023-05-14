package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField

class MessageDescriptor(obj: Any) : AbstractWrapper(obj) {
    val messageId: Long get() = instance.getObjectField("mMessageId") as Long
    val conversationId: SnapUUID get() = SnapUUID(instance.getObjectField("mConversationId"))
}