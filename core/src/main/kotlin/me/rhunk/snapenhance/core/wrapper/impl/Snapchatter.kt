package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter


class BitmojiInfo(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var avatarId by field<String?>("mAvatarId")
    @get:JSGetter @set:JSSetter
    var backgroundId by field<String?>("mBackgroundId")
    @get:JSGetter @set:JSSetter
    var sceneId by field<String?>("mSceneId")
    @get:JSGetter @set:JSSetter
    var selfieId by field<String?>("mSelfieId")
}

class Snapchatter(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter
    val bitmojiInfo by field<BitmojiInfo?>("mBitmojiInfo")
    @get:JSGetter @set:JSSetter
    var displayName by field<String?>("mDisplayName")
    @get:JSGetter @set:JSSetter
    var userId by field("mUserId") { SnapUUID(it) }
    @get:JSGetter @set:JSSetter
    var username by field<String>("mUsername")
}