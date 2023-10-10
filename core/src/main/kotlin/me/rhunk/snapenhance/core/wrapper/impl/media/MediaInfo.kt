package me.rhunk.snapenhance.core.wrapper.impl.media

import android.os.Parcelable
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import java.lang.reflect.Field


class MediaInfo(obj: Any?) : AbstractWrapper(obj) {
    val uri: String
        get() {
            val firstStringUriField = instanceNonNull().javaClass.fields.first { f: Field -> f.type == String::class.java }
            return instanceNonNull().getObjectField(firstStringUriField.name) as String
        }

    init {
        instance?.let {
            if (it is List<*>) {
                if (it.size == 0) {
                    throw RuntimeException("MediaInfo is empty")
                }
                instance = it[0]!!
            }
        }
    }

    val encryption: EncryptionWrapper?
        get() {
            val encryptionAlgorithmField = instanceNonNull().javaClass.fields.first { f: Field ->
                f.type.isInterface && Parcelable::class.java.isAssignableFrom(f.type)
            }
            return encryptionAlgorithmField[instance]?.let { EncryptionWrapper(it) }
        }
}
