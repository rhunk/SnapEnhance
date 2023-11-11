package me.rhunk.snapenhance.manager.ui.tab.impl.download

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.data.APKMirror
import me.rhunk.snapenhance.manager.data.DownloadItem
import me.rhunk.snapenhance.manager.patch.LSPatch
import me.rhunk.snapenhance.manager.ui.components.DowngradeNoticeDialog
import me.rhunk.snapenhance.manager.ui.tab.Tab
import okio.use
import java.io.File

class LSPatchTab : Tab("lspatch") {
    private var localItemFile: File? = null
    private var downloadItem: DownloadItem? = null
    private var snapEnhanceModule: File? = null
    private var patchedApk by mutableStateOf<File?>(null)
    private val apkMirror = APKMirror()

    private fun patch(log: (Any?) -> Unit, onProgress: (Float) -> Unit) {
        var apkFile: File? = localItemFile

        downloadItem?.let {
            log("Fetching download link for ${it.title}...")
            val downloadLink = apkMirror.fetchDownloadLink(it.downloadPage) ?: run {
                log("== Failed to fetch download link ==")
                return
            }
            log("Downloading apk...")

            val downloadResponse = apkMirror.okhttpClient.newCall(
                okhttp3.Request.Builder()
                    .url(downloadLink)
                    .build()
            ).execute()

            if (!downloadResponse.isSuccessful) {
                log("== Failed to download apk ==")
                log("Response code: ${downloadResponse.code}")
                return
            }

            apkFile = sharedConfig.apkCache.resolve("${it.hash}.apk")
            apkFile!!.outputStream().use { outputStream ->
                runCatching {
                    downloadResponse.body.byteStream().use { inputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var totalRead = 0L
                        val totalSize = downloadResponse.body.contentLength()
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            totalRead += read
                            onProgress(totalRead.toFloat() / totalSize.toFloat())
                        }
                    }
                }.onFailure { throwable ->
                    log("== Failed to download apk ==")
                    log(throwable)
                    return
                }
            }

            apkFile!!.renameTo(File(activity.externalCacheDir!!, "base.apk"))
        }

        log("== Downloaded apk ==")
        snapEnhanceModule?.let { module ->
            val lsPatch = LSPatch(activity, mapOf(
                sharedConfig.snapEnhancePackageName to module,
            ), printLog = {
                log("[LSPatch] $it")
            }, obfuscate = sharedConfig.obfuscateLSPatch)

            log("== Patching apk ==")
            val outputFiles = lsPatch.patchSplits(listOf(apkFile!!))

            patchedApk = outputFiles["base.apk"] ?: run {
                log("== Failed to patch apk ==")
                return
            }
            return
        }
        patchedApk = apkFile
    }

    @Composable
    @Suppress("DEPRECATION")
    override fun Content() {
        this.localItemFile = remember { getArguments()?.getString("localItemFile")?.let { File(it) } }
        this.downloadItem = remember { getArguments()?.getParcelable("downloadItem") }
        this.snapEnhanceModule = remember {
            getArguments()?.getString("modulePath")?.let {
                File(it)
            }
        }

        val coroutineScope = rememberCoroutineScope()
        var showDowngradeNoticeDialog by remember { mutableStateOf(false) }

        var status by remember { mutableStateOf("") }
        var progress by remember { mutableFloatStateOf(-1f) }

        LaunchedEffect(this.snapEnhanceModule) {
            patchedApk = null
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    patch(log = {
                        coroutineScope.launch {
                            status += when (it) {
                                is Throwable -> it.message + "\n" + it.stackTraceToString()
                                else -> it.toString()
                            } + "\n"
                        }
                    }) {
                        progress = it
                    }
                }.onFailure {
                    coroutineScope.launch {
                        status += it.message + "\n" + it.stackTraceToString()
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                coroutineScope.cancel()
            }
        }

        val scrollState = rememberScrollState()

        fun triggerInstallation(shouldUninstall: Boolean) {
            navigation.navigateTo(InstallPackageTab::class, args = Bundle().apply {
                putString("downloadPath", patchedApk?.absolutePath)
                putString("appPackage", sharedConfig.snapchatPackageName)
                putBoolean("uninstall", shouldUninstall)
            })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Text(text = status, overflow = TextOverflow.Visible, modifier = Modifier.padding(10.dp))
                }
            }
            if (progress != -1f) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.height(10.dp), strokeCap = StrokeCap.Round)
            }

            if (patchedApk != null) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    triggerInstallation(true)
                }) {
                    Text(text = "Uninstall & Install")
                }

                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    showDowngradeNoticeDialog = true
                }) {
                    Text(text = "Update")
                }
            }

            LaunchedEffect(status) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }

        if (showDowngradeNoticeDialog) {
            Dialog(onDismissRequest = { showDowngradeNoticeDialog = false }) {
                DowngradeNoticeDialog(onDismiss = { showDowngradeNoticeDialog = false }, onSuccess = {
                    triggerInstallation(false)
                })
            }
        }
    }
}