package me.rhunk.snapenhance.common.util

import android.os.Parcelable
import kotlinx.parcelize.parcelableCreator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun Parcelable.toSerialized(): String? {
    val parcel = android.os.Parcel.obtain()
    return try {
        writeToParcel(parcel, 0)
        parcel.marshall()?.let {
            Base64.encode(it)
        }
    } finally {
        parcel.recycle()
    }
}

@OptIn(ExperimentalEncodingApi::class)
inline fun <reified T : Parcelable> toParcelable(serialized: String): T? {
    val parcel = android.os.Parcel.obtain()
    return try {
        Base64.decode(serialized).let {
            parcel.unmarshall(it, 0, it.size)
        }
        parcel.setDataPosition(0)
        parcelableCreator<T>().createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}
