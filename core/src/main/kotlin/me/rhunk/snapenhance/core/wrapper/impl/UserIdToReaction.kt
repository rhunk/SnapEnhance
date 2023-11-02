package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class UserIdToReaction(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var userId by field("mUserId") { SnapUUID(it) }
    @get:JSGetter @set:JSSetter
    var reactionId get() = (instanceNonNull().getObjectField("mReaction")
        ?.getObjectField("mReactionContent")
        ?.getObjectField("mIntentionType") as Long?) ?: -1
    set(value) {
        instanceNonNull().getObjectField("mReaction")
            ?.getObjectField("mReactionContent")
            ?.setObjectField("mIntentionType", value)
    }
}