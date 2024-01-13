package me.rhunk.snapenhance.common.data

import java.io.File
import java.io.InputStream

enum class FileType(
    val fileExtension: String? = null,
    val mimeType: String,
    val isVideo: Boolean = false,
    val isImage: Boolean = false,
    val isAudio: Boolean = false
) {
    GIF("gif", "image/gif", false, false, false),
    PNG("png", "image/png", false, true, false),
    MP4("mp4", "video/mp4", true, false, false),
    MKV("mkv", "video/mkv", true, false, false),
    AVI("avi", "video/avi", true, false, false),
    MP3("mp3", "audio/mp3",false, false, true),
    OPUS("opus", "audio/opus", false, false, true),
    AAC("aac", "audio/aac", false, false, true),
    JPG("jpg", "image/jpg",false, true, false),
    ZIP("zip", "application/zip", false, false, false),
    WEBP("webp", "image/webp", false, true, false),
    MPD("mpd", "text/xml", false, false, false),
    UNKNOWN("dat", "application/octet-stream", false, false, false);

    companion object {
        private val fileSignatures = mapOf(
            "52494646" to WEBP,
            "504b0304" to ZIP,
            "89504e47" to PNG,
            "00000020" to MP4,
            "00000018" to MP4,
            "0000001c" to MP4,
            "494433" to MP3,
            "4f676753" to OPUS,
            "fff15" to AAC,
            "ffd8ff" to JPG,
            "47494638" to GIF,
            "1a45dfa3" to MKV,
        )

        fun fromString(string: String?): FileType {
            return entries.firstOrNull { it.fileExtension.equals(string, ignoreCase = true) } ?: UNKNOWN
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val result = StringBuilder()
            for (b in bytes) {
                result.append(String.format("%02x", b))
            }
            return result.toString()
        }

        fun fromFile(file: File): FileType {
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(16)
                inputStream.read(buffer)
                return fromByteArray(buffer)
            }
        }

        fun fromByteArray(array: ByteArray): FileType {
            val headerBytes = ByteArray(16)
            System.arraycopy(array, 0, headerBytes, 0, 16)
            val hex = bytesToHex(headerBytes)
            return fileSignatures.entries.firstOrNull { hex.startsWith(it.key) }?.value ?: UNKNOWN
        }

        fun fromInputStream(inputStream: InputStream): FileType {
            val buffer = ByteArray(16)
            inputStream.read(buffer)
            return fromByteArray(buffer)
        }
    }
}
