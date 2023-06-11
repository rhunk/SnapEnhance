package me.rhunk.snapenhance.data

enum class FileType(
    val fileExtension: String? = null,
    val isVideo: Boolean = false,
    val isImage: Boolean = false,
    val isAudio: Boolean = false
) {
    GIF("gif", false, false, false),
    PNG("png", false, true, false),
    MP4("mp4", true, false, false),
    MP3("mp3", false, false, true),
    JPG("jpg", false, true, false),
    ZIP("zip", false, false, false),
    WEBP("webp", false, true, false),
    MPD("mpd", false, true, false),
    UNKNOWN("dat", false, false, false);

    companion object {
        private val fileSignatures = HashMap<String, FileType>()

        init {
            fileSignatures["52494646"] = WEBP
            fileSignatures["504b0304"] = ZIP
            fileSignatures["89504e47"] = PNG
            fileSignatures["00000020"] = MP4
            fileSignatures["00000018"] = MP4
            fileSignatures["0000001c"] = MP4
            fileSignatures["ffd8ffe0"] = JPG
        }

        fun fromString(string: String?): FileType {
            return values().firstOrNull { it.fileExtension.equals(string, ignoreCase = true) } ?: UNKNOWN
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val result = StringBuilder()
            for (b in bytes) {
                result.append(String.format("%02x", b))
            }
            return result.toString()
        }

        fun fromByteArray(array: ByteArray): FileType {
            val headerBytes = ByteArray(16)
            System.arraycopy(array, 0, headerBytes, 0, 16)
            val hex = bytesToHex(headerBytes)
            return fileSignatures.entries.firstOrNull { hex.startsWith(it.key) }?.value ?: UNKNOWN
        }
    }
}
