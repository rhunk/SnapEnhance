package me.rhunk.snapenhance.core.util.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import me.rhunk.snapenhance.common.data.FileType
import java.io.File
import kotlin.math.max

object PreviewUtils {
    fun createPreview(data: ByteArray, isVideo: Boolean): Bitmap? {
        if (!isVideo) {
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        return MediaMetadataRetriever().apply {
            setDataSource(object : MediaDataSource() {
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
        }.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    fun createPreviewFromFile(file: File): Bitmap? {
        return if (FileType.fromFile(file).isVideo) {
            MediaMetadataRetriever().apply {
                setDataSource(file.absolutePath)
            }.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } else {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options())
        }
    }

    fun resizeBitmap(source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val sourceWidth = source.getWidth()
        val sourceHeight = source.getHeight()
        val scale = max(outWidth.toFloat() / sourceWidth, outHeight.toFloat() / sourceHeight)

        val dx = (outWidth - (scale * sourceWidth)) / 2F
        val dy = (outHeight - (scale * sourceHeight)) / 2F
        val dest = Bitmap.createBitmap(outWidth, outHeight, source.getConfig())
        val canvas = Canvas(dest)
        canvas.drawBitmap(source, Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }, null)
        return dest
    }

    fun mergeBitmapOverlay(originalMedia: Bitmap, overlayLayer: Bitmap): Bitmap {
        val biggestBitmap = if (originalMedia.width * originalMedia.height > overlayLayer.width * overlayLayer.height) originalMedia else overlayLayer
        val smallestBitmap = if (biggestBitmap == originalMedia) overlayLayer else originalMedia

        val mergedBitmap = Bitmap.createBitmap(biggestBitmap.width, biggestBitmap.height, biggestBitmap.config)

        with(Canvas(mergedBitmap)) {
            val scaleMatrix = Matrix().apply {
                postScale(biggestBitmap.width.toFloat() / smallestBitmap.width.toFloat(), biggestBitmap.height.toFloat() / smallestBitmap.height.toFloat())
            }

            if (biggestBitmap == originalMedia) {
                drawBitmap(originalMedia, 0f, 0f, null)
                drawBitmap(overlayLayer, scaleMatrix, null)
            } else {
                drawBitmap(originalMedia, scaleMatrix, null)
                drawBitmap(overlayLayer, 0f, 0f, null)
            }
        }

        return mergedBitmap
    }
}
