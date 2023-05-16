package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField

class Message(obj: Any?) : AbstractWrapper(obj) {
    val orderKey get() = instanceNonNull().getObjectField("mOrderKey") as Long
    val senderId get() = SnapUUID(instanceNonNull().getObjectField("mSenderId"))
    val messageContent get() = MessageContent(instanceNonNull().getObjectField("mMessageContent"))
    val messageDescriptor get() = MessageDescriptor(instanceNonNull().getObjectField("mDescriptor"))
    val messageMetadata get() = MessageMetadata(instanceNonNull().getObjectField("mMetadata"))
    val messageState get() = getEnumValue("mState", MessageState.COMMITTED)
}