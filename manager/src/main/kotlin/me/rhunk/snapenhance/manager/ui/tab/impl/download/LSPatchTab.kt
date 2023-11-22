package me.rhunk.snapenhance.manager.ui.tab.impl.download

import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.data.APKMirror
import me.rhunk.snapenhance.manager.data.DownloadItem
import me.rhunk.snapenhance.manager.patch.LSPatch
import me.rhunk.snapenhance.manager.ui.components.DowngradeNoticeDialog
import me.rhunk.snapenhance.manager.ui.tab.Tab
import okio.use
import java.io.File
import kotlin.properties.Delegates

class LSPatchTab : Tab("lspatch") {
    private val apkMirror = APKMirror()

    private fun patch(
        log: (Any?) -> Unit,
        onProgress: (Float) -> Unit,
        downloadItem: DownloadItem? = null,
        snapEnhanceModule: File? = null,
        localItemFile: File? = null,
        patchedApk: MutableState<File?>,
    ) {
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

            patchedApk.value = outputFiles["base.apk"] ?: run {
                log("== Failed to patch apk ==")
                return
            }
            return
        }
        patchedApk.value = apkFile
    }

    @Suppress("DEPRECATION")
    override fun build(navGraphBuilder: NavGraphBuilder) {
        var currentJob: Job? = null
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        val patchedApk = mutableStateOf<File?>(null)
        val status = mutableStateOf("")
        var progress by mutableFloatStateOf(-1f)
        var isRunning by Delegates.observable(false) { _, _, newValue ->
            if (!newValue) {
                currentJob?.cancel()
                currentJob = null
                progress = -1f
            }
        }

        navGraphBuilder.composable(route) {
            var showDowngradeNoticeDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (isRunning) return@LaunchedEffect
                status.value = ""
                coroutineScope.launch(Dispatchers.IO) {
                    isRunning = true
                    runCatching {
                        patch(
                            localItemFile = getArguments()?.getString("localItemFile")?.let { File(it) } ,
                            log = {
                                coroutineScope.launch {
                                    status.value += when (it) {
                                        is Throwable -> it.message + "\n" + it.stackTraceToString()
                                        else -> it.toString()
                                    } + "\n"
                                }
                            },
                            downloadItem = getArguments()?.getParcelable("downloadItem"),
                            snapEnhanceModule = getArguments()?.getString("modulePath")?.let {
                                File(it)
                            },
                            patchedApk = patchedApk,
                            onProgress = { progress = it }
                        )
                    }.onFailure {
                        coroutineScope.launch {
                            status.value += it.message + "\n" + it.stackTraceToString()
                        }
                    }
                    isRunning = false
                }.also { currentJob = it }
            }

            DisposableEffect(Unit) {
                onDispose {
                    if (isRunning) return@onDispose
                    patchedApk.value = null
                }
            }

            val scrollState = rememberScrollState()

            fun triggerInstallation(shouldUninstall: Boolean) {
                navigation.navigateTo(InstallPackageTab::class, args = Bundle().apply {
                    putString("downloadPath", patchedApk.value?.absolutePath)
                    putString("appPackage", sharedConfig.snapchatPackageName)
                    putBoolean("uninstall", shouldUninstall)
                })
            }
            BackHandler(isRunning) {}
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
                        Text(text = status.value, overflow = TextOverflow.Visible, modifier = Modifier.padding(10.dp))
                    }
                }
                if (progress != -1f) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.height(10.dp), strokeCap = StrokeCap.Round)
                }

                if (patchedApk.value != null) {
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
}