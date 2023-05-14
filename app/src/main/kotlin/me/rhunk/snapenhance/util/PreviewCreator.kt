package me.rhunk.snapenhance.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever

object PreviewUtils {
    fun createPreview(data: ByteArray, isVideo: Boolean): Bitmap? {
        if (!isVideo) {
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(object : MediaDataSource() {
            override fun readAt(
                position: Long,
                buffer: ByteArray,
                offset: Int,
                size: Int
            ): Int {
                var newSize = size
                val length = data.size
                if (position >= length) {
                    return -1
                }
                if (position + newSize > length) {
                    newSize = length - position.toInt()
                }
                System.arraycopy(data, position.toInt(), buffer, offset, newSize)
                return newSize
            }

            override fun getSize(): Long {
                return data.size.toLong()
            }

            override fun close() {}
        })
        return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
}
