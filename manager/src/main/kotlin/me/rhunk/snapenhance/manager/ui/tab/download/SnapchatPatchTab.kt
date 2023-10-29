package me.rhunk.snapenhance.manager.ui.tab.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.manager.R
import me.rhunk.snapenhance.manager.data.APKMirror
import me.rhunk.snapenhance.manager.data.DownloadItem
import me.rhunk.snapenhance.manager.ui.Tab
import me.rhunk.snapenhance.manager.ui.components.ConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
class SnapchatPatchTab : Tab("snapchat_download_tab") {
    private val apkCache by lazy {
        activity.cacheDir.resolve("snapchat_apk_cache").also {
            if (!it.exists()) it.mkdirs()
        }
    }
    private val apkMirror =  APKMirror()
    private val cachedDownloadItems = mutableListOf<DownloadItem>()
    private var currentPage by mutableIntStateOf(1)

    private fun isDownloaded(hash: String): Boolean {
        return apkCache.resolve("${hash}.apk").exists()
    }

    private fun deleteDownload(hash: String) {
        runCatching {
            apkCache.resolve("${hash}.apk").delete()
        }.onFailure {
            it.printStackTrace()
            toast("Failed to delete download. It may have already been deleted.")
        }
    }

    private suspend fun downloadSnapchatVersion(downloadItem: DownloadItem, onProgress: (Float) -> Unit, onSuccess: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            toast("Downloading ${downloadItem.title}...")
            val downloadLink = apkMirror.fetchDownloadLink(downloadItem.downloadPage) ?: run {
                toast("Failed to fetch download link")
                return@withContext
            }

            val downloadResponse = apkMirror.okhttpClient.newCall(
                okhttp3.Request.Builder()
                    .url(downloadLink)
                    .build()
            ).execute()

            if (!downloadResponse.isSuccessful) {
                toast("Failed to download")
                return@withContext
            }

            val apkFile = apkCache.resolve("${downloadItem.hash}.apk")
            apkFile.outputStream().use { outputStream ->
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
                }.onFailure {
                    it.printStackTrace()
                    toast("Failed to save apk")
                    return@withContext
                }
            }

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    @Composable
    override fun TopBar() {
        var deleteAllDialog by remember { mutableStateOf(false) }
        IconButton(onClick = { deleteAllDialog = true }) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
        }

        if (deleteAllDialog) {
            AlertDialog(onDismissRequest = { deleteAllDialog = false }) {
                ConfirmationDialog(title = "Are you sure you want to delete all downloads?", onDismiss = { deleteAllDialog = false }) {
                    deleteAllDialog = false
                    runCatching {
                        apkCache.listFiles()?.forEach { it.deleteRecursively() }
                    }.onFailure {
                        toast("Failed to delete downloads")
                        it.printStackTrace()
                    }
                    toast("Done!")
                }
            }
        }
    }

    @Composable
    private fun DownloadItemRow(coroutineScope: CoroutineScope, item: DownloadItem) {
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(-1f) }
        var isDownloaded by remember { mutableStateOf(isDownloaded(item.hash)) }

        var showDeleteCurrentDownloadDialog by remember { mutableStateOf(false) }

        if (showDeleteCurrentDownloadDialog) {
            AlertDialog(onDismissRequest = { showDeleteCurrentDownloadDialog = false }) {
                ConfirmationDialog(title = "Are you sure you want to delete this download?", onDismiss = { showDeleteCurrentDownloadDialog = false }) {
                    coroutineScope.launch {
                        deleteDownload(item.hash)
                        isDownloaded = false
                    }
                    showDeleteCurrentDownloadDialog = false
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.padding(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.sclogo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(item.shortTitle)
                    Text(item.releaseDate)
                    if (!item.isBeta) {
                        Text("Recommended", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Row(
                    modifier = Modifier.padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (!isDownloaded) {
                        if (isDownloading) {
                            if (downloadProgress != -1f) {
                                CircularProgressIndicator(progress = downloadProgress, modifier = Modifier.size(40.dp))
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            }
                        } else {
                            FilledIconButton(onClick = {
                                coroutineScope.launch {
                                    isDownloading = true
                                    downloadProgress = -1f
                                    downloadSnapchatVersion(item, onProgress = {
                                        downloadProgress = it
                                    }, onSuccess = { isDownloaded = true })
                                    isDownloading = false
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                            }
                        }
                    } else {
                        Button(onClick = { /*TODO*/ }) {
                            Text("Patch")
                        }
                        IconButton(onClick = { showDeleteCurrentDownloadDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        var isFetching by remember { mutableStateOf(false) }
        val downloadItems = remember { cachedDownloadItems.toMutableStateList() }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Select a version to download and patch")
            LazyColumn {
                items(downloadItems, key = { it.hash }) { item ->
                    DownloadItemRow(coroutineScope, item)
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.alpha(if (isFetching) 1f else 0f))
                    }
                    SideEffect {
                        if (isFetching) return@SideEffect
                        isFetching = true
                        coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    apkMirror.fetchSnapchatVersions(currentPage)?.let {
                                        withContext(Dispatchers.Main) {
                                            cachedDownloadItems.addAll(it)
                                            downloadItems.addAll(it)
                                        }
                                    }
                                }
                            }.onFailure {
                                it.printStackTrace()
                            }
                            ++currentPage
                            isFetching = false
                        }
                    }
                }
            }
        }
    }
}