package me.rhunk.snapenhance.nativelib

data class NativeConfig(
    val disableBitmoji: Boolean = false,
    val disableMetrics: Boolean = false,
    val hookAssetOpen: Boolean = false,
)