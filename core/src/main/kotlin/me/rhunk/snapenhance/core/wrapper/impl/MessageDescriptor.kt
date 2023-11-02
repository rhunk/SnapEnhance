package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class MessageDescriptor(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var messageId by field<Long>("mMessageId")
    val conversationId by field("mConversationId") { SnapUUID(it) }
}