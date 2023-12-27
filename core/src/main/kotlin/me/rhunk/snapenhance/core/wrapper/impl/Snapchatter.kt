package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper



class BitmojiInfo(obj: Any?) : AbstractWrapper(obj) {
    var avatarId by field<String?>("mAvatarId")
    var backgroundId by field<String?>("mBackgroundId")
    var sceneId by field<String?>("mSceneId")
    var selfieId by field<String?>("mSelfieId")
}

class Snapchatter(obj: Any?) : AbstractWrapper(obj) {
    val bitmojiInfo by field<BitmojiInfo?>("mBitmojiInfo")
    var displayName by field<String?>("mDisplayName")
    var userId by field("mUserId") { SnapUUID(it) }
    var username by field<String>("mUsername")
}