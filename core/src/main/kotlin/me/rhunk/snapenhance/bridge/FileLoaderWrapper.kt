package me.rhunk.snapenhance.bridge

import android.content.Context
import me.rhunk.snapenhance.bridge.types.BridgeFileType

open class FileLoaderWrapper(
    private val fileType: BridgeFileType,
    private val defaultContent: ByteArray
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

    fun loadFromBridge(bridgeClient: BridgeClient) {
        isFileExists = { bridgeClient.isFileExists(fileType) }
        read = { bridgeClient.createAndReadFile(fileType, defaultContent) }
        write = { bridgeClient.writeFile(fileType, it) }
        delete = { bridgeClient.deleteFile(fileType) }
    }
}