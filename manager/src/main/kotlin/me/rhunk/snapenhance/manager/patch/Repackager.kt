package me.rhunk.snapenhance.manager.patch

import android.content.Context
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.wind.meditor.core.ManifestEditor
import com.wind.meditor.property.AttributeItem
import com.wind.meditor.property.ModificationProperty
import me.rhunk.snapenhance.manager.BuildConfig
import me.rhunk.snapenhance.manager.patch.util.ApkSignatureHelper.provideSigningExtension
import me.rhunk.snapenhance.manager.patch.util.obfuscateDexFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class Repackager(
    private val context: Context,
    private val cacheFolder: File,
    private val packageName: String,
) {
    private fun patchManifest(data: ByteArray): ByteArray {
        val property = ModificationProperty()

        property.addManifestAttribute(AttributeItem("package", packageName).apply {
            type = 3
            namespace = null
        })

        return ByteArrayOutputStream().apply {
            ManifestEditor(ByteArrayInputStream(data), this, property).processManifest()
            flush()
            close()
        }.toByteArray()
    }

    fun patch(apkFile: File): File {
        val outputFile = File(cacheFolder, "patched-${apkFile.name}")
        runCatching {
            patch(apkFile, outputFile)
        }.onFailure {
            outputFile.delete()
            throw it
        }
        return outputFile
    }

    fun patch(apkFile: File, outputFile: File) {
        val dstZFile = ZFile.openReadWrite(outputFile, ZFileOptions().setAlignmentRule(
            AlignmentRules.compose(AlignmentRules.constantForSuffix(".so", 4096))
        ))
        provideSigningExtension(context.assets.open("lspatch/keystore.jks")).register(dstZFile)
        val srcZFile = ZFile.openReadOnly(apkFile)
        val dexFiles = mutableListOf<File>()

        for (entry in srcZFile.entries()) {
            val name = entry.centralDirectoryHeader.name
            if (name.startsWith("AndroidManifest.xml")) {
                dstZFile.add(name, ByteArrayInputStream(
                    patchManifest(entry.read())
                ), false)
                continue
            }
            if (name.startsWith("classes") && name.endsWith(".dex")) {
                println("obfuscating $name")
                val inputStream = entry.open() ?: continue
                val obfuscatedDexFile = inputStream.obfuscateDexFile(cacheFolder, { dexFile ->
                    dexFile.classes.firstOrNull { it.type == "Lme/rhunk/snapenhance/common/Constants;" } != null
                }, mapOf(
                    BuildConfig.APPLICATION_ID to packageName
                ))?.also { dexFiles.add(it) }

                if (obfuscatedDexFile == null) {
                    inputStream.close()
                    dstZFile.add(name, entry.open(), false)
                    continue
                }

                dstZFile.add(name, obfuscatedDexFile.inputStream(), false)
                continue
            }
            dstZFile.add(name, entry.open(), false)
        }
        dstZFile.realign()
        dstZFile.close()
        srcZFile.close()
        dexFiles.forEach { it.delete() }
    }
}