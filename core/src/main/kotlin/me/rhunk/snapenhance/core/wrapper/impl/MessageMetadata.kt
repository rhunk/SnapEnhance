package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.PlayableSnapState
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class MessageMetadata(obj: Any?) : AbstractWrapper(obj){
    @get:JSGetter @set:JSSetter
    var createdAt by field<Long>("mCreatedAt")
    @get:JSGetter @set:JSSetter
    var readAt by field<Long>("mReadAt")
    @get:JSGetter @set:JSSetter
    var playableSnapState by enum("mPlayableSnapState", PlayableSnapState.PLAYABLE)

    @get:JSGetter @set:JSSetter
    var savedBy by field("mSavedBy", uuidArrayListMapper)
    @get:JSGetter @set:JSSetter
    var openedBy by field("mOpenedBy", uuidArrayListMapper)
    @get:JSGetter @set:JSSetter
    var seenBy by field("mSeenBy", uuidArrayListMapper)
    @get:JSGetter @set:JSSetter
    var screenRecordedBy by field("mScreenRecordedBy", uuidArrayListMapper)
    @get:JSGetter @set:JSSetter
    var screenShottedBy by field("mScreenShottedBy", uuidArrayListMapper)
    @get:JSGetter @set:JSSetter
    var reactions by field("mReactions") {
        (it as ArrayList<*>).map { i -> UserIdToReaction(i) }.toMutableList()
    }
    @get:JSGetter @set:JSSetter
    var isSaveable by field<Boolean>("mIsSaveable")
    @get:JSGetter @set:JSSetter
    var isEditable by field<Boolean>("mIsEditable")
    @get:JSGetter @set:JSSetter
    var isEdited by field<Boolean>("mIsEdited")
    @get:JSGetter @set:JSSetter
    var isErasable by field<Boolean>("mIsErasable")
    @get:JSGetter @set:JSSetter
    var isFriendLinkPending by field<Boolean>("mIsFriendLinkPending")
    @get:JSGetter @set:JSSetter
    var isReactable by field<Boolean>("mIsReactable")
}