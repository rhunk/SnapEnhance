package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class QuotedMessage(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var content by field("mContent") { QuotedMessageContent(it) }
}