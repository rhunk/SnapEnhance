package me.rhunk.snapenhance.core.wrapper.impl.media

import me.rhunk.snapenhance.common.data.download.MediaEncryptionKeyPair
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Field
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class EncryptionWrapper(instance: Any?) : AbstractWrapper(instance) {
    fun decrypt(data: ByteArray?): ByteArray {
        return newCipher(Cipher.DECRYPT_MODE).doFinal(data)
    }

    fun decrypt(inputStream: InputStream?): InputStream {
        return CipherInputStream(inputStream, newCipher(Cipher.DECRYPT_MODE))
    }

    fun decrypt(outputStream: OutputStream?): OutputStream {
        return CipherOutputStream(outputStream, newCipher(Cipher.DECRYPT_MODE))
    }

    /**
     * Search for a byte[] field with the specified length
     *
     * @param arrayLength the length of the byte[] field
     * @return the field
     */
    private fun searchByteArrayField(arrayLength: Int): Field {
        return instanceNonNull()::class.java.fields.first { f ->
            try {
                if (!f.type.isArray || f.type
                        .componentType != Byte::class.javaPrimitiveType
                ) return@first false
                return@first (f.get(instanceNonNull()) as ByteArray).size == arrayLength
            } catch (e: Exception) {
                return@first false
            }
        }
    }

    /**
     * Create a new cipher with the specified mode
     */
    fun newCipher(mode: Int): Cipher {
        val cipher = cipher
        cipher.init(mode, SecretKeySpec(keySpec, "AES"), IvParameterSpec(ivKeyParameterSpec))
        return cipher
    }

    /**
     * Get the cipher from the encryption wrapper
     */
    private val cipher: Cipher
        get() = Cipher.getInstance("AES/CBC/PKCS5Padding")

    /**
     * Get the key spec from the encryption wrapper
     */
    val keySpec: ByteArray by lazy {
        searchByteArrayField(32)[instance] as ByteArray
    }

    /**
     * Get the iv key parameter spec from the encryption wrapper
     */
    val ivKeyParameterSpec: ByteArray by lazy {
        searchByteArrayField(16)[instance] as ByteArray
    }
}


@OptIn(ExperimentalEncodingApi::class)
fun EncryptionWrapper.toKeyPair()
        = MediaEncryptionKeyPair(Base64.UrlSafe.encode(this.keySpec), Base64.UrlSafe.encode(this.ivKeyParameterSpec))
