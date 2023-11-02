package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.MessageState
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class Message(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var orderKey by field<Long>("mOrderKey")
    @get:JSGetter @set:JSSetter
    var senderId by field("mSenderId") { SnapUUID(it) }
    @get:JSGetter @set:JSSetter
    var messageContent by field("mMessageContent") { MessageContent(it) }
    @get:JSGetter @set:JSSetter
    var messageDescriptor by field("mDescriptor") { MessageDescriptor(it) }
    @get:JSGetter @set:JSSetter
    var messageMetadata by field("mMetadata") { MessageMetadata(it) }
    @get:JSGetter @set:JSSetter
    var messageState by enum("mState", MessageState.COMMITTED)
}