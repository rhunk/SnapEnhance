package me.rhunk.snapenhance.nativelib

data class NativeRequestData(
    val uri: String,
    var buffer: ByteArray,
    var canceled: Boolean = false,
)