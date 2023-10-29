package me.rhunk.snapenhance.manager.lspatch

import android.content.Context
import com.android.tools.build.apkzlib.sign.SigningExtension
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.google.gson.Gson
import com.wind.meditor.core.ManifestEditor
import com.wind.meditor.property.AttributeItem
import com.wind.meditor.property.ModificationProperty
import me.rhunk.snapenhance.manager.lspatch.config.Constants.ORIGINAL_APK_ASSET_PATH
import me.rhunk.snapenhance.manager.lspatch.config.Constants.PROXY_APP_COMPONENT_FACTORY
import me.rhunk.snapenhance.manager.lspatch.config.PatchConfig
import me.rhunk.snapenhance.manager.lspatch.util.ApkSignatureHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


//https://github.com/LSPosed/LSPatch/blob/master/patch/src/main/java/org/lsposed/patch/LSPatch.java
class LSPatch(
    private val context: Context,
    private val modules: Map<String, File>, //packageName -> file
    private val printLog: (Any) -> Unit
) {
    companion object {
        private val Z_FILE_OPTIONS = ZFileOptions().setAlignmentRule(
            AlignmentRules.compose(
                AlignmentRules.constantForSuffix(".so", 4096),
                AlignmentRules.constantForSuffix(ORIGINAL_APK_ASSET_PATH, 4096)
            )
        )
    }

    private fun patchManifest(data: ByteArray, lspatchMetadata: String): ByteArray {
        val property = ModificationProperty()

        property.addApplicationAttribute(AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY))
        property.addMetaData(ModificationProperty.MetaData("lspatch", lspatchMetadata))

        return ByteArrayOutputStream().apply {
            ManifestEditor(ByteArrayInputStream(data), this, property).processManifest()
            flush()
            close()
        }.toByteArray()
    }

    private fun provideSigningExtension(): SigningExtension {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(context.assets.open("lspatch/keystore.jks"), "android".toCharArray())
        val key = keyStore.getEntry("androiddebugkey", KeyStore.PasswordProtection("android".toCharArray())) as KeyStore.PrivateKeyEntry
        val certificates = key.certificateChain.mapNotNull { it as? X509Certificate }.toTypedArray()

        return SigningExtension(
            SigningOptions.builder().apply {
                setMinSdkVersion(28)
                setV2SigningEnabled(true)
                setCertificates(*certificates)
                setKey(key.privateKey)
            }.build()
        )
    }

    private fun resignApk(inputApkFile: File, outputFile: File) {
        printLog("Resigning ${inputApkFile.absolutePath} to ${outputFile.absolutePath}")
        val dstZFile = ZFile.openReadWrite(outputFile, Z_FILE_OPTIONS)
        val inZFile = ZFile.openReadOnly(inputApkFile)

        inZFile.entries().forEach { entry ->
            dstZFile.add(entry.centralDirectoryHeader.name, entry.open())
        }

        // sign apk
        runCatching {
            provideSigningExtension().register(dstZFile)
        }.onFailure {
            throw Exception("Failed to sign apk", it)
        }

        dstZFile.realign()
        dstZFile.close()
        inZFile.close()
        printLog("Done")
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalEncodingApi::class)
    private fun patchApk(inputApkFile: File, outputFile: File) {
        printLog("Patching ${inputApkFile.absolutePath} to ${outputFile.absolutePath}")
        val dstZFile = ZFile.openReadWrite(outputFile, Z_FILE_OPTIONS)
        val sourceApkFile = dstZFile.addNestedZip({ ORIGINAL_APK_ASSET_PATH }, inputApkFile, false)

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
            provideSigningExtension().register(dstZFile)
        }.onFailure {
            throw Exception("Failed to sign apk", it)
        }

        printLog("Patching manifest")

        val originalManifestEntry = sourceApkFile.get("AndroidManifest.xml") ?: throw Exception("No original manifest found")
        originalManifestEntry.open().use { inputStream ->
            val patchedManifestData = patchManifest(inputStream.readBytes(), Base64.encode(patchConfig.toByteArray()))
            dstZFile.add("AndroidManifest.xml", patchedManifestData.inputStream())
        }

        //add config
        printLog("Adding config")
        dstZFile.add("assets/lspatch/config.json", ByteArrayInputStream(patchConfig.toByteArray()))

        // add loader dex
        printLog("Adding dex files")
        dstZFile.add("classes.dex", context.assets.open("lspatch/dexes/metaloader.dex"))
        dstZFile.add("assets/lspatch/loader.dex", context.assets.open("lspatch/dexes/loader.dex"))

        //add natives
        printLog("Adding natives")
        context.assets.list("lspatch/so")?.forEach { native ->
            dstZFile.add("assets/lspatch/so/$native/liblspatch.so", context.assets.open("lspatch/so/$native/liblspatch.so"), false)
        }

        //embed modules
        printLog("embedding modules")
        modules.forEach { (packageName, module) ->
            dstZFile.add("assets/lspatch/modules/$packageName.apk", module.inputStream())
        }

        // link apk entries
        printLog("Linking apk entries")

        for (entry in sourceApkFile.entries()) {
            val name = entry.centralDirectoryHeader.name
            if (name.startsWith("classes") && name.endsWith(".dex")) continue
            if (dstZFile[name] != null) continue
            if (name == "AndroidManifest.xml") continue
            if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(
                    ".RSA"
                ))
            ) continue
            sourceApkFile.addFileLink(name, name)
        }

        dstZFile.realign()
        dstZFile.close()
        printLog("Done")
    }

    fun patchSplits(inputs: List<File>): List<File> {
        val outputs = mutableListOf<File>()
        inputs.forEach { input ->
            val outputFile = File.createTempFile("patched", ".apk", context.cacheDir)
            if (input.name.contains("split")) {
                resignApk(input, outputFile)
                outputs.add(outputFile)
                return@forEach
            }
            patch(input, outputFile)
            outputs.add(outputFile)
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
        }.onSuccess {
            outputFile.delete()
        }
    }
}