package me.rhunk.snapenhance.core.util.snap

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.download.data.MediaEncryptionKeyPair
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object EncryptionHelper {
    fun getEncryptionKeys(contentType: ContentType, messageProto: ProtoReader, isArroyo: Boolean): Pair<ByteArray, ByteArray>? {
        val mediaEncryptionInfo = MediaDownloaderHelper.getMessageMediaEncryptionInfo(
            messageProto,
            contentType,
            isArroyo
        ) ?: return null
        val encryptionProtoIndex = if (mediaEncryptionInfo.contains(Constants.ENCRYPTION_PROTO_INDEX_V2)) {
            Constants.ENCRYPTION_PROTO_INDEX_V2
        } else {
            Constants.ENCRYPTION_PROTO_INDEX
        }
        val encryptionProto = mediaEncryptionInfo.followPath(encryptionProtoIndex) ?: return null

        var key: ByteArray = encryptionProto.getByteArray(1)!!
        var iv: ByteArray = encryptionProto.getByteArray(2)!!

        if (encryptionProtoIndex == Constants.ENCRYPTION_PROTO_INDEX_V2) {
            key = Base64.UrlSafe.decode(key)
            iv = Base64.UrlSafe.decode(iv)
        }

        return Pair(key, iv)
    }

    fun decryptInputStream(
        inputStream: InputStream,
        contentType: ContentType,
        messageProto: ProtoReader,
        isArroyo: Boolean
    ): InputStream {
        val encryptionKeys = getEncryptionKeys(contentType, messageProto, isArroyo) ?: throw Exception("Failed to get encryption keys")

        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKeys.first, "AES"), IvParameterSpec(encryptionKeys.second))
        }.let { cipher ->
            return CipherInputStream(inputStream, cipher)
        }
    }

    fun decryptInputStream(
        inputStream: InputStream,
        mediaEncryptionKeyPair: MediaEncryptionKeyPair?
    ): InputStream {
        if (mediaEncryptionKeyPair == null) {
            return inputStream
        }

        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE,
                SecretKeySpec(Base64.UrlSafe.decode(mediaEncryptionKeyPair.key), "AES"),
                IvParameterSpec(Base64.UrlSafe.decode(mediaEncryptionKeyPair.iv))
            )
        }.let { cipher ->
            return CipherInputStream(inputStream, cipher)
        }
    }
}
