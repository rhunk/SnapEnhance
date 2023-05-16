package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.data.PlayableSnapState
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField

class MessageMetadata(obj: Any?) : AbstractWrapper(obj){
    val createdAt: Long get() = instanceNonNull().getObjectField("mCreatedAt") as Long
    val readAt: Long get() = instanceNonNull().getObjectField("mReadAt") as Long
    var playableSnapState: PlayableSnapState
    get() = getEnumValue("mPlayableSnapState", PlayableSnapState.PLAYABLE)
    set(value) {
        setEnumValue("mPlayableSnapState", value)
    }
    val savedBy: List<SnapUUID> = (instanceNonNull().getObjectField("mSavedBy") as List<*>).map { SnapUUID(it!!) }
}