package me.rhunk.snapenhance.nativelib

import android.util.Log

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}
    var nativeShouldLoadAsset: (String) -> Boolean = { true }

    companion object {
        var initialized = false
            private set
    }

    fun initOnce(classloader: ClassLoader) {
        if (initialized) throw IllegalStateException("NativeLib already initialized")
        runCatching {
            System.loadLibrary(BuildConfig.NATIVE_NAME)
            init(classloader)
            initialized = true
        }.onFailure {
            Log.e("SnapEnhance", "NativeLib init failed")
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
        if (nativeRequestData.canceled || !nativeRequestData.buffer.contentEquals(buffer)) return nativeRequestData
        return null
    }

    @Suppress("unused")
    private fun shouldLoadAsset(name: String) = runCatching {
        nativeShouldLoadAsset(name)
    }.getOrNull() ?: true

    fun loadNativeConfig(config: NativeConfig) {
        if (!initialized) return
        loadConfig(config)
    }

    private external fun init(classLoader: ClassLoader)
    private external fun loadConfig(config: NativeConfig)
}