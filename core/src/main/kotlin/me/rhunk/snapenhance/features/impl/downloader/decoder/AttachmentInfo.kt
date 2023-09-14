package me.rhunk.snapenhance.features.impl.downloader.decoder

import me.rhunk.snapenhance.core.download.data.MediaEncryptionKeyPair

data class BitmojiSticker(
    val reference: String,
) : AttachmentInfo()

open class AttachmentInfo(
    val encryption: MediaEncryptionKeyPair? = null,
    val resolution: Pair<Int, Int>? = null,
    val duration: Long? = null
) {
    override fun toString() = "AttachmentInfo(encryption=$encryption, resolution=$resolution, duration=$duration)"
}