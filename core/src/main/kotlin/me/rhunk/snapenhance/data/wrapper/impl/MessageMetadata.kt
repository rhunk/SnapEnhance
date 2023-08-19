package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.PlayableSnapState
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ktx.getObjectField

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