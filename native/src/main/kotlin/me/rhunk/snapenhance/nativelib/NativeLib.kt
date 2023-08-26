package me.rhunk.snapenhance.nativelib

class NativeLib {
    fun initOnce(classloader: ClassLoader) {
        System.loadLibrary("nativelib")
        init(classloader)
    }

    external fun init(classLoader: ClassLoader)
    external fun loadConfig(config: NativeConfig)
}