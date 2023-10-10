package me.rhunk.snapenhance.common.logger

enum class LogChannel(
    val channel: String,
    val shortName: String
) {
    CORE("SnapEnhanceCore", "core"),
    NATIVE("SnapEnhanceNative", "native"),
    MANAGER("SnapEnhanceManager", "manager"),
    XPOSED("LSPosed-Bridge", "xposed");

    companion object {
        fun fromChannel(channel: String): LogChannel? {
            return entries.find { it.channel == channel }
        }
    }
}