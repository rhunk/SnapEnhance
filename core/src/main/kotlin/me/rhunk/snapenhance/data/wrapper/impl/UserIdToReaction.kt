package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class UserIdToReaction(obj: Any?) : AbstractWrapper(obj) {
    val userId = SnapUUID(instanceNonNull().getObjectField("mUserId"))
    val reactionId = (instanceNonNull().getObjectField("mReaction")
        ?.getObjectField("mReactionContent")
        ?.getObjectField("mIntentionType") as Long?) ?: 0
}