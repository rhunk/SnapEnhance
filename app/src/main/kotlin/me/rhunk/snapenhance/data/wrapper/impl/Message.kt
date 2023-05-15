package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField

class Message(obj: Any) : AbstractWrapper(obj) {
    val orderKey get() = instance.getObjectField("mOrderKey") as Long
    val senderId get() = SnapUUID(instance.getObjectField("mSenderId"))
    val messageContent get() = MessageContent(instance.getObjectField("mMessageContent"))
    val messageDescriptor get() = MessageDescriptor(instance.getObjectField("mDescriptor"))
    val messageMetadata get() = MessageMetadata(instance.getObjectField("mMetadata"))
    val messageState get() = getEnumValue("mMessageState", MessageState.COMMITTED)
}