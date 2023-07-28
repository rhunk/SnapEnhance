package me.rhunk.snapenhance.manager.data


data class SnapchatAppInfo(
    val version: String,
    val versionCode: Long
)

data class ModMappingsInfo(
    val generatedSnapchatVersion: Long,
    val isOutdated: Boolean
)

data class InstallationSummary(
    val snapchatInfo: SnapchatAppInfo?,
    val mappingsInfo: ModMappingsInfo?
)
