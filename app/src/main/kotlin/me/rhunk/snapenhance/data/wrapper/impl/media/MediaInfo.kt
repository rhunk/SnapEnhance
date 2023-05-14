package me.rhunk.snapenhance.data.wrapper.impl.media

import android.os.Parcelable
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField
import java.lang.reflect.Field


class MediaInfo(obj: Any) : AbstractWrapper(obj) {
    val uri: String
        get() {
            val firstStringUriField = instance.javaClass.fields.first { f: Field -> f.type == String::class.java }
            return instance.getObjectField(firstStringUriField.name) as String
        }

    init {
        var mediaInfo: Any = instance
        if (mediaInfo is List<*>) {
            if (mediaInfo.size == 0) {
                throw RuntimeException("MediaInfo is empty")
            }
            mediaInfo = mediaInfo[0]!!
        }
        instance = mediaInfo
    }

    val encryption: EncryptionWrapper?
        get() {
            val encryptionAlgorithmField = instance.javaClass.fields.first { f: Field ->
                f.type.isInterface && Parcelable::class.java.isAssignableFrom(f.type)
            }
            return encryptionAlgorithmField[instance]?.let { EncryptionWrapper(it) }
        }
}
