package me.rhunk.snapenhance.nativelib

class NativeLib {
    fun init() {
        System.loadLibrary("nativelib")
    }


    external fun loadConfig(config: NativeConfig)
}