package me.rhunk.snapenhance.core.util.snap

import me.rhunk.snapenhance.core.download.data.SplitMediaAssetType
import me.rhunk.snapenhance.data.FileType
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object MediaDownloaderHelper {
    fun getFileType(bufferedInputStream: BufferedInputStream): FileType {
        val buffer = ByteArray(16)
        bufferedInputStream.mark(16)
        bufferedInputStream.read(buffer)
        bufferedInputStream.reset()
        return FileType.fromByteArray(buffer)
    }


    fun getSplitElements(
        inputStream: InputStream,
        callback: (SplitMediaAssetType, InputStream) -> Unit
    ) {
        val bufferedInputStream = BufferedInputStream(inputStream)
        val fileType = getFileType(bufferedInputStream)

        if (fileType != FileType.ZIP) {
            callback(SplitMediaAssetType.ORIGINAL, bufferedInputStream)
            return
        }

        val zipInputStream = ZipInputStream(bufferedInputStream)

        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            if (entry.name.startsWith("overlay")) {
                callback(SplitMediaAssetType.OVERLAY, zipInputStream)
            } else if (entry.name.startsWith("media")) {
                callback(SplitMediaAssetType.ORIGINAL, zipInputStream)
            }
            entry = zipInputStream.nextEntry
        }
    }
}