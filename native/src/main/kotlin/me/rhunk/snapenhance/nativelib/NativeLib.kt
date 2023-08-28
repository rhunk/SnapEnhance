package me.rhunk.snapenhance.nativelib

import android.util.Log

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}
    companion object {
        private var initialized = false
    }

    fun initOnce(classloader: ClassLoader) {
        if (initialized) throw IllegalStateException("NativeLib already initialized")
        runCatching {
            System.loadLibrary(BuildConfig.NATIVE_NAME)
            init(classloader)
            initialized = true
        }.onFailure {
            Log.e("SnapEnhance", "NativeLib init failed", it)
        }
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

    fun loadNativeConfig(config: NativeConfig) {
        if (!initialized) return
        loadConfig(config)
    }

    private external fun init(classLoader: ClassLoader)
    private external fun loadConfig(config: NativeConfig)
}