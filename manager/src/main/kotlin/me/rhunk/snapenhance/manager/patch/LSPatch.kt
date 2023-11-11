package me.rhunk.snapenhance.manager.patch

import android.content.Context
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.google.gson.Gson
import com.wind.meditor.core.ManifestEditor
import com.wind.meditor.property.AttributeItem
import com.wind.meditor.property.ModificationProperty
import me.rhunk.snapenhance.manager.patch.config.Constants.PROXY_APP_COMPONENT_FACTORY
import me.rhunk.snapenhance.manager.patch.config.PatchConfig
import me.rhunk.snapenhance.manager.patch.util.ApkSignatureHelper
import me.rhunk.snapenhance.manager.patch.util.ApkSignatureHelper.provideSigningExtension
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random


//https://github.com/LSPosed/LSPatch/blob/master/patch/src/main/java/org/lsposed/patch/LSPatch.java
class LSPatch(
    private val context: Context,
    private val modules: Map<String, File>, //packageName -> file
    private val obfuscate: Boolean,
    private val printLog: (Any) -> Unit
) {

    private fun patchManifest(data: ByteArray, lspatchMetadata: Pair<String, String>): ByteArray {
        val property = ModificationProperty()

        property.addApplicationAttribute(AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY))
        property.addMetaData(ModificationProperty.MetaData(lspatchMetadata.first, lspatchMetadata.second))

        return ByteArrayOutputStream().apply {
            ManifestEditor(ByteArrayInputStream(data), this, property).processManifest()
            flush()
            close()
        }.toByteArray()
    }

    private fun resignApk(inputApkFile: File, outputFile: File) {
        printLog("Resigning ${inputApkFile.absolutePath} to ${outputFile.absolutePath}")
        val dstZFile = ZFile.openReadWrite(outputFile, ZFileOptions())
        val inZFile = ZFile.openReadOnly(inputApkFile)

        inZFile.entries().forEach { entry ->
            dstZFile.add(entry.centralDirectoryHeader.name, entry.open())
        }

        // sign apk
        runCatching {
            provideSigningExtension(context.assets.open("lspatch/keystore.jks")).register(dstZFile)
        }.onFailure {
            throw Exception("Failed to sign apk", it)
        }

        dstZFile.realign()
        dstZFile.close()
        inZFile.close()
        printLog("Done")
    }

    private fun uniqueHash(): String {
        return Random.nextBytes(Random.nextInt(5, 10)).joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalEncodingApi::class)
    private fun patchApk(inputApkFile: File, outputFile: File) {
        printLog("Patching ${inputApkFile.absolutePath} to ${outputFile.absolutePath}")

        val obfuscationCacheFolder = File(context.cacheDir, "lspatch").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val lspatchObfuscation = LSPatchObfuscation(obfuscationCacheFolder) { printLog(it) }
        val dexObfuscationConfig = if (obfuscate) DexObfuscationConfig(
            packageName = uniqueHash(),
            metadataManifestField = uniqueHash(),
            metaLoaderFilePath = uniqueHash(),
            configFilePath = uniqueHash(),
            loaderFilePath = uniqueHash(),
            libNativeFilePath = mapOf(
                "arm64-v8a" to uniqueHash() + ".so",
                "armeabi-v7a" to uniqueHash() + ".so",
            ),
            originApkPath = uniqueHash(),
            cachedOriginApkPath = uniqueHash(),
            openAtApkPath = uniqueHash(),
            assetModuleFolderPath = uniqueHash(),
        ) else null

        val dstZFile = ZFile.openReadWrite(outputFile, ZFileOptions().setAlignmentRule(
            AlignmentRules.compose(
                AlignmentRules.constantForSuffix(".so", 4096),
                AlignmentRules.constantForSuffix("assets/" + (dexObfuscationConfig?.originApkPath ?: "lspatch/origin.apk"), 4096)
            )
        ))

        val patchConfig = PatchConfig(
            useManager = false,
            debuggable = false,
            overrideVersionCode = false,
            sigBypassLevel = 2,
            originalSignature = ApkSignatureHelper.getApkSignInfo(inputApkFile.absolutePath),
            appComponentFactory = "androidx.core.app.CoreComponentFactory"
        ).let { Gson().toJson(it) }

        // sign apk
        runCatching {
            provideSigningExtension(context.assets.open("lspatch/keystore.jks")).register(dstZFile)
        }.onFailure {
            throw Exception("Failed to sign apk", it)
        }

        printLog("Patching manifest")

        val sourceApkFile = dstZFile.addNestedZip({ "assets/" + (dexObfuscationConfig?.originApkPath ?: "lspatch/origin.apk") }, inputApkFile, false)
        val originalManifestEntry = sourceApkFile.get("AndroidManifest.xml") ?: throw Exception("No original manifest found")
        originalManifestEntry.open().use { inputStream ->
            val patchedManifestData = patchManifest(inputStream.readBytes(), (dexObfuscationConfig?.metadataManifestField ?: "lspatch") to Base64.encode(patchConfig.toByteArray()))
            dstZFile.add("AndroidManifest.xml", patchedManifestData.inputStream())
        }

        //add config
        printLog("Adding config")
        dstZFile.add("assets/" + (dexObfuscationConfig?.configFilePath ?: "lspatch/config.json"), ByteArrayInputStream(patchConfig.toByteArray()))

        // add loader dex
        printLog("Adding loader dex")
        context.assets.open("lspatch/dexes/loader.dex").use { inputStream ->
            dstZFile.add("assets/" + (dexObfuscationConfig?.loaderFilePath ?: "lspatch/loader.dex"), dexObfuscationConfig?.let {
                lspatchObfuscation.obfuscateLoader(inputStream, it).inputStream()
            } ?: inputStream)
        }

        //add natives
        printLog("Adding natives")
        context.assets.list("lspatch/so")?.forEach { native ->
            dstZFile.add("assets/${dexObfuscationConfig?.libNativeFilePath?.get(native) ?: "lspatch/so/$native/liblspatch.so"}", context.assets.open("lspatch/so/$native/liblspatch.so"), false)
        }

        //embed modules
        printLog("Embedding modules")
        modules.forEach { (packageName, module) ->
            val obfuscatedPackageName = dexObfuscationConfig?.packageName ?: packageName
            printLog("- $obfuscatedPackageName")
            dstZFile.add("assets/${dexObfuscationConfig?.assetModuleFolderPath ?: "lspatch/modules"}/$obfuscatedPackageName.apk", module.inputStream())
        }

        // link apk entries
        printLog("Linking apk entries")

        for (entry in sourceApkFile.entries()) {
            val name = entry.centralDirectoryHeader.name
            if (dexObfuscationConfig == null && name.startsWith("classes") && name.endsWith(".dex")) continue
            if (dstZFile[name] != null) continue
            if (name == "AndroidManifest.xml") continue
            if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(
                    ".RSA"
                ))
            ) continue
            sourceApkFile.addFileLink(name, name)
        }

        printLog("Adding meta loader dex")
        context.assets.open("lspatch/dexes/metaloader.dex").use { inputStream ->
            dstZFile.add(dexObfuscationConfig?.let { "classes9.dex" } ?: "classes.dex", dexObfuscationConfig?.let {
                lspatchObfuscation.obfuscateMetaLoader(inputStream, it).inputStream()
            } ?: inputStream)
        }

        printLog("Writing apk")
        dstZFile.realign()
        dstZFile.close()
        sourceApkFile.close()

        printLog("Cleaning obfuscation cache")
        obfuscationCacheFolder.deleteRecursively()
        printLog("Done")
    }

    fun patchSplits(inputs: List<File>): Map<String, File> {
        val outputs = mutableMapOf<String, File>()
        inputs.forEach { input ->
            val outputFile = File.createTempFile("patched", ".apk", context.externalCacheDir ?: context.cacheDir)
            if (input.name.contains("split")) {
                resignApk(input, outputFile)
                outputs[input.name] = outputFile
                return@forEach
            }
            patch(input, outputFile)
            outputs["base.apk"] = outputFile
        }
        return outputs
    }

    private fun patch(input: File, outputFile: File) {
        //check if input apk is already patched
        var isAlreadyPatched = false
        var inputFile = input

        // extract origin
        printLog("Extracting origin apk")
        ZipFile(input).use { zipFile ->
            zipFile.getEntry("assets/lspatch/origin.apk")?.apply {
                inputFile = File.createTempFile("origin", ".apk")
                inputFile.outputStream().use {
                    zipFile.getInputStream(this).copyTo(it)
                }
                isAlreadyPatched = true
            }
        }

        if (outputFile.exists()) outputFile.delete()

        printLog("Patching apk")
        runCatching {
            patchApk(inputFile, outputFile)
        }.onFailure {
            if (isAlreadyPatched) {
                inputFile.delete()
            }
            outputFile.delete()
            printLog("Failed to patch")
            printLog(it)
        }
    }
}