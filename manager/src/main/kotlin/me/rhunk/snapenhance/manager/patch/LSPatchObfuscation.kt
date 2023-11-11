package me.rhunk.snapenhance.manager.patch

import me.rhunk.snapenhance.manager.patch.util.obfuscateDexFile
import java.io.File
import java.io.InputStream

data class DexObfuscationConfig(
    val packageName: String,
    val metadataManifestField: String? = null,
    val metaLoaderFilePath: String? = null,
    val configFilePath: String? = null,
    val loaderFilePath: String? = null,
    val originApkPath: String? = null,
    val cachedOriginApkPath: String? = null,
    val openAtApkPath: String? = null,
    val assetModuleFolderPath: String? = null,
    val libNativeFilePath: Map<String, String> = mapOf(),
)

class LSPatchObfuscation(
    private val cacheFolder: File,
    private val printLog: (String) -> Unit = { println(it) }
) {

    fun obfuscateMetaLoader(inputStream: InputStream, config: DexObfuscationConfig): File {
        return inputStream.obfuscateDexFile(cacheFolder, mapOf(
            "assets/lspatch/config.json" to "assets/${config.configFilePath}",
            "assets/lspatch/loader.dex" to "assets/${config.loaderFilePath}",
        ) + (config.libNativeFilePath.takeIf { it.isNotEmpty() }?.let {
            mapOf(
                "!/assets/lspatch/so/" to "!/assets/",
                "assets/lspatch/so/" to "assets/",
                "/liblspatch.so" to "",
                "arm64-v8a" to config.libNativeFilePath["arm64-v8a"],
                "armeabi-v7a" to config.libNativeFilePath["armeabi-v7a"],
                "x86" to config.libNativeFilePath["x86"],
                "x86_64" to config.libNativeFilePath["x86_64"],
            )
        } ?: mapOf()))
    }

    fun obfuscateLoader(inputStream: InputStream, config: DexObfuscationConfig): File {
        return inputStream.obfuscateDexFile(cacheFolder, mapOf(
            "assets/lspatch/config.json" to config.configFilePath?.let { "assets/$it" },
            "assets/lspatch/loader.dex" to config.loaderFilePath?.let { "assets/$it" },
            "assets/lspatch/metaloader.dex" to config.metaLoaderFilePath?.let { "assets/$it" },
            "assets/lspatch/origin.apk" to config.originApkPath?.let { "assets/$it" },
            "/lspatch/origin/" to config.cachedOriginApkPath?.let { "/$it/" }, // context.getCacheDir() + ==> "/lspatch/origin/" <== + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc() + ".apk";
            "/lspatch/" to config.cachedOriginApkPath?.let { "/$it/" }, // context.getCacheDir() + "/lspatch/" + packageName + "/"
            "cache/lspatch/origin/" to config.cachedOriginApkPath?.let { "cache/$it" }, //LSPApplication => Path originPath = Paths.get(appInfo.dataDir, "cache/lspatch/origin/");
            "assets/lspatch/modules/" to config.assetModuleFolderPath?.let { "assets/$it/" }, // Constants.java => EMBEDDED_MODULES_ASSET_PATH
            "lspatch/modules" to config.assetModuleFolderPath, // LocalApplicationService.java => context.getAssets().list("lspatch/modules"),
            "lspatch/modules/" to config.assetModuleFolderPath?.let { "$it/" }, // LocalApplicationService.java => try (var is = context.getAssets().open("lspatch/modules/" + name)) {
            "lspatch" to config.metadataManifestField, // SigBypass.java => "lspatch",
            "org.lsposed.lspatch" to config.cachedOriginApkPath?.let { "$it/${config.packageName}/" }, // Constants.java => "org.lsposed.lspatch", (Used in LSPatchUpdater.kt)
        ))
    }
}