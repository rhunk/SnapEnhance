package me.rhunk.snapenhance.manager.lspatch

import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.dexlib2.writer.pool.DexPool
import org.jf.dexlib2.writer.pool.StringPool
import java.io.BufferedInputStream
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
    private fun obfuscateDexFile(dexStrings: Map<String, String?>, inputStream: InputStream): File {
        val dexFile = DexBackedDexFile.fromInputStream(Opcodes.forApi(29), BufferedInputStream(inputStream))

        val dexPool = object: DexPool(dexFile.opcodes) {
            override fun getSectionProvider(): SectionProvider {
                val dexPool = this
                return object: DexPoolSectionProvider() {
                    override fun getStringSection() = object: StringPool(dexPool) {
                        private val cacheMap = mutableMapOf<String, String>()

                        override fun intern(string: CharSequence) {
                            dexStrings[string.toString()]?.let {
                                cacheMap[string.toString()] = it
                                printLog("mapping $string to $it")
                                super.intern(it)
                                return
                            }
                            super.intern(string)
                        }

                        override fun getItemIndex(key: CharSequence): Int {
                            return cacheMap[key.toString()]?.let {
                                internedItems[it]
                            } ?: super.getItemIndex(key)
                        }

                        override fun getItemIndex(key: StringReference): Int {
                            return cacheMap[key.toString()]?.let {
                                internedItems[it]
                            } ?: super.getItemIndex(key)
                        }
                    }
                }
            }
        }
        dexFile.classes.forEach { dexBackedClassDef ->
            dexPool.internClass(dexBackedClassDef)
        }
        val outputFile = File.createTempFile("obf", ".dex", cacheFolder)
        dexPool.writeTo(FileDataStore(outputFile))
        return outputFile
    }


    fun obfuscateMetaLoader(inputStream: InputStream, config: DexObfuscationConfig): File {
        return obfuscateDexFile(mapOf(
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
        } ?: mapOf()), inputStream)
    }

    fun obfuscateLoader(inputStream: InputStream, config: DexObfuscationConfig): File {
        return obfuscateDexFile(mapOf(
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
        ), inputStream)
    }
}