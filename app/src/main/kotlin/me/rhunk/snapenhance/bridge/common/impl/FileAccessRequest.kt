package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class FileAccessRequest(
    var action: FileAccessAction? = null,
    var fileType: FileType? = null,
    var content: ByteArray? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putInt("action", action!!.value)
        bundle.putInt("fileType", fileType!!.value)
        bundle.putByteArray("content", content)
    }

    override fun read(bundle: Bundle) {
        action = FileAccessAction.fromValue(bundle.getInt("action"))
        fileType = FileType.fromValue(bundle.getInt("fileType"))
        content = bundle.getByteArray("content")
    }

    enum class FileType(val value: Int) {
        CONFIG(0), MAPPINGS(1), STEALTH(2);

        companion object {
            fun fromValue(value: Int): FileType? {
                return values().firstOrNull { it.value == value }
            }
        }
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
