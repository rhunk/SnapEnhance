package me.rhunk.snapenhance.bridge.common.impl.file

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class FileAccessRequest(
    var action: FileAccessAction? = null,
    var fileType: BridgeFileType? = null,
    var content: ByteArray? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putInt("action", action!!.value)
        bundle.putInt("fileType", fileType!!.value)
        bundle.putByteArray("content", content)
    }

    override fun read(bundle: Bundle) {
        action = FileAccessAction.fromValue(bundle.getInt("action"))
        fileType = BridgeFileType.fromValue(bundle.getInt("fileType"))
        content = bundle.getByteArray("content")
    }

    enum class FileAccessAction(val value: Int) {
        READ(0), WRITE(1), DELETE(2), EXISTS(3);

        companion object {
            fun fromValue(value: Int): FileAccessAction? {
                return values().firstOrNull { it.value == value }
            }
        }
    }
}
