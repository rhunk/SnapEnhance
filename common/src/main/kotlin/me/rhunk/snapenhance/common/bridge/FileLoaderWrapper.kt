package me.rhunk.snapenhance.common.bridge

import android.content.Context
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType

open class FileLoaderWrapper(
    val fileType: BridgeFileType,
    val defaultContent: ByteArray
) {
    lateinit var isFileExists: () -> Boolean
    lateinit var write: (ByteArray) -> Unit
    lateinit var read: () -> ByteArray
    lateinit var delete: () -> Unit

    fun loadFromContext(context: Context) {
        val file = fileType.resolve(context)
        isFileExists = { file.exists() }
        read = {
            if (!file.exists()) {
                file.createNewFile()
                file.writeBytes("{}".toByteArray(Charsets.UTF_8))
            }
            file.readBytes()
        }
        write = { file.writeBytes(it) }
        delete = { file.delete() }
    }

}