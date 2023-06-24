@file:OptIn(ExperimentalEncodingApi::class)

package me.rhunk.snapenhance.download.data

import me.rhunk.snapenhance.data.wrapper.impl.media.EncryptionWrapper
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// key and iv are base64 encoded
data class MediaEncryptionKeyPair(
    val key: String,
    val iv: String
)

fun Pair<ByteArray, ByteArray>.toKeyPair(): MediaEncryptionKeyPair {
    return MediaEncryptionKeyPair(Base64.UrlSafe.encode(this.first), Base64.UrlSafe.encode(this.second))
}

fun EncryptionWrapper.toKeyPair(): MediaEncryptionKeyPair {
    return MediaEncryptionKeyPair(Base64.UrlSafe.encode(this.keySpec), Base64.UrlSafe.encode(this.ivKeyParameterSpec))
}