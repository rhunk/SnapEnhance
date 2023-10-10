package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.PlayableSnapState
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class MessageMetadata(obj: Any?) : AbstractWrapper(obj){
    val createdAt: Long get() = instanceNonNull().getObjectField("mCreatedAt") as Long
    val readAt: Long get() = instanceNonNull().getObjectField("mReadAt") as Long
    var playableSnapState by enum("mPlayableSnapState", PlayableSnapState.PLAYABLE)

    private fun getUUIDList(name: String): List<SnapUUID> {
        return (instanceNonNull().getObjectField(name) as List<*>).map { SnapUUID(it!!) }
    }

    val savedBy: List<SnapUUID> by lazy {
        getUUIDList("mSavedBy")
    }
    val openedBy: List<SnapUUID> by lazy {
        getUUIDList("mOpenedBy")
    }
    val seenBy: List<SnapUUID> by lazy {
        getUUIDList("mSeenBy")
    }
    val reactions: List<UserIdToReaction> by lazy {
        (instanceNonNull().getObjectField("mReactions") as List<*>).map { UserIdToReaction(it!!) }
    }
}