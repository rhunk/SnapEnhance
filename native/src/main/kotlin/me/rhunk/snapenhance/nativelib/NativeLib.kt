package me.rhunk.snapenhance.nativelib

import android.util.Log

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}
    var nativeShouldLoadAsset: (String) -> Boolean = { true }

    companion object {
        var initialized = false
            private set
    }

    fun initOnce() {
        if (initialized) throw IllegalStateException("NativeLib already initialized")
        runCatching {
            System.loadLibrary(BuildConfig.NATIVE_NAME)
            init()
            initialized = true
        }.onFailure {
            Log.e("SnapEnhance", "NativeLib init failed")
        }
    }

    @Suppress("unused")
    private fun onNativeUnaryCall(uri: String, buffer: ByteArray): NativeRequestData? {
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

    fun lockNativeDatabase(name: String, callback: () -> Unit) {
        if (!initialized) return
        lockDatabase(name) {
            runCatching {
                callback()
            }.onFailure {
                Log.e("SnapEnhance", "lockNativeDatabase callback failed", it)
            }
        }
    }

    private external fun init()
    private external fun loadConfig(config: NativeConfig)
    private external fun lockDatabase(name: String, callback: Runnable)
}