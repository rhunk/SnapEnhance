package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class Message(obj: Any?) : AbstractWrapper(obj) {
    val orderKey get() = instanceNonNull().getObjectField("mOrderKey") as Long
    val senderId get() = SnapUUID(instanceNonNull().getObjectField("mSenderId"))
    val messageContent get() = MessageContent(instanceNonNull().getObjectField("mMessageContent"))
    val messageDescriptor get() = MessageDescriptor(instanceNonNull().getObjectField("mDescriptor"))
    val messageMetadata get() = MessageMetadata(instanceNonNull().getObjectField("mMetadata"))
    var messageState by enum("mState", MessageState.COMMITTED)
}