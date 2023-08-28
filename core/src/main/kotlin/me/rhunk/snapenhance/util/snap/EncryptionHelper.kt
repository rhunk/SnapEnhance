package me.rhunk.snapenhance.util.snap

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.InputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    fun getEncryptionKeys(contentType: ContentType, messageProto: ProtoReader, isArroyo: Boolean): Pair<ByteArray, ByteArray>? {
        val messageMediaInfo = MediaDownloaderHelper.getMessageMediaInfo(messageProto, contentType, isArroyo) ?: return null
        val encryptionProtoIndex = if (messageMediaInfo.contains(Constants.ENCRYPTION_PROTO_INDEX_V2)) {
            Constants.ENCRYPTION_PROTO_INDEX_V2
        } else {
            Constants.ENCRYPTION_PROTO_INDEX
        }
        val encryptionProto = messageMediaInfo.followPath(encryptionProtoIndex) ?: return null

        var key: ByteArray = encryptionProto.getByteArray(1)!!
        var iv: ByteArray = encryptionProto.getByteArray(2)!!

        if (encryptionProtoIndex == Constants.ENCRYPTION_PROTO_INDEX_V2) {
            val decoder = Base64.getMimeDecoder()
            key = decoder.decode(key)
            iv = decoder.decode(iv)
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
}
