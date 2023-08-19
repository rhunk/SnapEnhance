package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ktx.getObjectField

class UserIdToReaction(obj: Any?) : AbstractWrapper(obj) {
    val userId = SnapUUID(instanceNonNull().getObjectField("mUserId"))
    val reactionId = (instanceNonNull().getObjectField("mReaction")
        ?.getObjectField("mReactionContent")
        ?.getObjectField("mIntentionType") as Long?) ?: 0
}