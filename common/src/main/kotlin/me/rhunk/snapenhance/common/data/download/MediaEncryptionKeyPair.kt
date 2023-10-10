@file:OptIn(ExperimentalEncodingApi::class)

package me.rhunk.snapenhance.common.data.download

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// key and iv are base64 encoded into url safe strings
data class MediaEncryptionKeyPair(
    val key: String,
    val iv: String
) {
    fun decryptInputStream(inputStream: InputStream): InputStream {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.UrlSafe.decode(key), "AES"), IvParameterSpec(Base64.UrlSafe.decode(iv)))
        return CipherInputStream(inputStream, cipher)
    }
}

fun Pair<ByteArray, ByteArray>.toKeyPair()
    = MediaEncryptionKeyPair(Base64.UrlSafe.encode(this.first), Base64.UrlSafe.encode(this.second))
