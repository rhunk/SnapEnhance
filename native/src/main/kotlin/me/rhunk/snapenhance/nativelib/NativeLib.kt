package me.rhunk.snapenhance.nativelib

import android.util.Log

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}

    fun initOnce(classloader: ClassLoader) {
        System.loadLibrary("nativelib")
        init(classloader)
    }

    @Suppress("unused")
    private fun onNativeUnaryCall(uri: String, buffer: ByteArray): NativeRequestData? {
        // Log.d("SnapEnhance", "onNativeUnaryCall: uri=$uri, bufferSize=${buffer.size}, buffer=${buffer.contentToString()}")
        val nativeRequestData = NativeRequestData(uri, buffer)
        runCatching {
            nativeUnaryCallCallback(nativeRequestData)
        }.onFailure {
            Log.e("SnapEnhance", "nativeUnaryCallCallback failed", it)
        }
        if (!nativeRequestData.buffer.contentEquals(buffer) || nativeRequestData.canceled) return nativeRequestData
        return null
    }


    external fun init(classLoader: ClassLoader)
    external fun loadConfig(config: NativeConfig)
}