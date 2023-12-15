package me.rhunk.snapenhance.core.util

import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.bridge.BridgeClient
import java.io.File
import java.util.zip.ZipFile

object LSPatchUpdater {
    private const val TAG = "LSPatchUpdater"

    var HAS_LSPATCH = false
        private set

    private fun getModuleUniqueHash(module: ZipFile): String {
        return module.entries().asSequence()
            .filter { !it.isDirectory }
            .map { it.crc }
            .reduce { acc, crc -> acc xor crc }
            .toString(16)
    }

    fun onBridgeConnected(context: ModContext, bridgeClient: BridgeClient) {
        val obfuscatedModulePath by lazy {
            (runCatching {
                context::class.java.classLoader?.loadClass("org.lsposed.lspatch.share.Constants")
            }.getOrNull())?.declaredFields?.firstOrNull { it.name == "MANAGER_PACKAGE_NAME" }?.also {
                it.isAccessible = true
            }?.get(null) as? String
        }

        val embeddedModule = context.androidContext.cacheDir
            .resolve("lspatch")
            .resolve(Constants.SE_PACKAGE_NAME).let { moduleDir ->
                if (!moduleDir.exists()) return@let null
                moduleDir.listFiles()?.firstOrNull { it.extension == "apk" }
            } ?: obfuscatedModulePath?.let { path ->
                context.androidContext.cacheDir.resolve(path).let dir@{ moduleDir ->
                    if (!moduleDir.exists()) return@dir null
                    moduleDir.listFiles()?.firstOrNull { it.extension == "apk" }
                } ?: return
            } ?: return

        HAS_LSPATCH = true
        context.log.verbose("Found embedded SE at ${embeddedModule.absolutePath}", TAG)

        val seAppApk = File(bridgeClient.getApplicationApkPath()).also {
            if (!it.canRead()) {
                throw IllegalStateException("Cannot read SnapEnhance apk")
            }
        }

        runCatching {
            if (getModuleUniqueHash(ZipFile(embeddedModule)) == getModuleUniqueHash(ZipFile(seAppApk))) {
                context.log.verbose("Embedded SE is up to date", TAG)
                return
            }
        }.onFailure {
            throw IllegalStateException("Failed to compare module signature", it)
        }

        context.log.verbose("updating", TAG)
        context.shortToast("Updating SnapEnhance. Please wait...")
        // copy embedded module to cache
        runCatching {
            seAppApk.copyTo(embeddedModule, overwrite = true)
        }.onFailure {
            seAppApk.delete()
            context.log.error("Failed to copy embedded module", it, TAG)
            context.longToast("Failed to update SnapEnhance. Please check logcat for more details.")
            context.forceCloseApp()
            return
        }

        context.longToast("SnapEnhance updated!")
        context.log.verbose("updated", TAG)
        context.forceCloseApp()
    }
}