package me.rhunk.snapenhance.util

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.InputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    fun decryptInputStreamFromArroyo(
        inputStream: InputStream,
        contentType: ContentType,
        messageProto: ProtoReader
    ): InputStream {
        var resultInputStream = inputStream
        val encryptionProtoPath: IntArray = when (contentType) {
            ContentType.NOTE -> Constants.ARROYO_NOTE_ENCRYPTION_PROTO_PATH
            ContentType.SNAP -> Constants.ARROYO_SNAP_ENCRYPTION_PROTO_PATH
            ContentType.EXTERNAL_MEDIA -> Constants.ARROYO_EXTERNAL_MEDIA_ENCRYPTION_PROTO_PATH
            else -> throw IllegalArgumentException("Invalid content type: $contentType")
        }

        //decrypt the content if needed
        messageProto.readPath(*encryptionProtoPath)?.let {
            val encryptionProtoIndex: Int = if (it.exists(Constants.ARROYO_ENCRYPTION_PROTO_INDEX_V2)) {
                Constants.ARROYO_ENCRYPTION_PROTO_INDEX_V2
            } else if (it.exists(Constants.ARROYO_ENCRYPTION_PROTO_INDEX)) {
                Constants.ARROYO_ENCRYPTION_PROTO_INDEX
            } else {
                return resultInputStream
            }
            resultInputStream = decryptInputStream(
                resultInputStream,
                encryptionProtoIndex == Constants.ARROYO_ENCRYPTION_PROTO_INDEX_V2,
                it,
                encryptionProtoIndex
            )
        }
        return resultInputStream
    }

    fun decryptInputStream(
        inputStream: InputStream,
        base64Encryption: Boolean,
        mediaInfoProto: ProtoReader,
        encryptionProtoIndex: Int
    ): InputStream {
        val mediaEncryption = mediaInfoProto.readPath(encryptionProtoIndex)!!
        var key: ByteArray = mediaEncryption.getByteArray(1)!!
        var iv: ByteArray = mediaEncryption.getByteArray(2)!!

        //audio note and external medias have their key and iv encoded in base64
        if (base64Encryption) {
            val decoder = Base64.getMimeDecoder()
            key = decoder.decode(key)
            iv = decoder.decode(iv)
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return CipherInputStream(inputStream, cipher)
    }
}
