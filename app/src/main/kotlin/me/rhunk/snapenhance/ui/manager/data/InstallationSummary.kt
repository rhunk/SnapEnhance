package me.rhunk.snapenhance.ui.manager.data


data class SnapchatAppInfo(
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val isLSPatched: Boolean,
    val isSplitApk: Boolean?
)

data class ModInfo(
    val loaderPackageName: String?,
    val buildPackageName: String,
    val buildVersion: String,
    val buildVersionCode: Long,
    val buildIssuer: String,
    val isDebugBuild: Boolean,
    val mappingVersion: Long?,
    val mappingsOutdated: Boolean?,
)

data class PlatformInfo(
    val device: String,
    val androidVersion: String,
    val systemAbi: String,
)

data class InstallationSummary(
    val platformInfo: PlatformInfo,
    val snapchatInfo: SnapchatAppInfo?,
    val modInfo: ModInfo?,
)
