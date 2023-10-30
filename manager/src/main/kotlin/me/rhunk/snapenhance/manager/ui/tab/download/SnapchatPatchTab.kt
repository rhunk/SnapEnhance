package me.rhunk.snapenhance.manager.ui.tab.download

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.manager.R
import me.rhunk.snapenhance.manager.data.APKMirror
import me.rhunk.snapenhance.manager.data.DownloadItem
import me.rhunk.snapenhance.manager.ui.Tab
import me.rhunk.snapenhance.manager.ui.components.ConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
class SnapchatPatchTab : Tab("snapchat_download") {
    private val apkMirror =  APKMirror()
    private val cachedDownloadItems = mutableListOf<DownloadItem>()
    private var currentPage by mutableIntStateOf(1)

    override fun init(activity: ComponentActivity) {
        super.init(activity)
        registerNestedTab(LSPatchTab::class)
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
                        sharedConfig.apkCache.listFiles()?.forEach { it.deleteRecursively() }
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
    private fun DownloadItemRow(item: DownloadItem, onSelected: () -> Unit = {}) {
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
                    if (!item.isBeta) {
                        Text("Recommended", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Row(
                    modifier = Modifier.padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    FilledIconButton(onClick = { onSelected() }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }
    }


    @Composable
    private fun SelectSnapchatVersionDialog(onSelected: (DownloadItem) -> Unit = {}) {
        val coroutineScope = rememberCoroutineScope()
        var isFetching by remember { mutableStateOf(false) }
        val downloadItems = remember { cachedDownloadItems.toMutableStateList() }

        LazyColumn {
            items(downloadItems, key = { it.hash }) { item ->
                DownloadItemRow(item) {
                    onSelected(item)
                }
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

    @Composable
    override fun Content() {
        var showSelectSnapchatVersionDialog by remember { mutableStateOf(false) }
        var selectedSnapchatVersion by remember { mutableStateOf(null as DownloadItem?) }
        val installedSnapEnhanceVersion = remember { runCatching { activity.packageManager.getPackageInfo(
            sharedConfig.snapEnhancePackageName, 0) }.getOrNull() }

        if (showSelectSnapchatVersionDialog) {
            AlertDialog(onDismissRequest = { showSelectSnapchatVersionDialog = false }) {
                SelectSnapchatVersionDialog {
                    selectedSnapchatVersion = it
                    showSelectSnapchatVersionDialog = false
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Select a version to download and patch")

            ElevatedCard(
                modifier = Modifier.padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.sclogo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                    if (selectedSnapchatVersion == null) {
                        Text(text = "Snapchat")
                    }
                    Text(text = selectedSnapchatVersion?.shortTitle ?: "Not Selected")
                    Button(onClick = { showSelectSnapchatVersionDialog = true }) {
                        Text("Choose")
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "SnapEnhance")
                    Text(text = installedSnapEnhanceVersion?.versionName ?: "Not installed")
                }
            }

            Column(
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSnapchatVersion != null && installedSnapEnhanceVersion != null,
                    onClick = {
                        navigation.navigateTo(LSPatchTab::class, args = Bundle().apply {
                            putParcelable("downloadItem", selectedSnapchatVersion)
                            putString("modulePath", installedSnapEnhanceVersion?.applicationInfo?.sourceDir)
                        }, noHistory = true)
                    }
                ) {
                    Text("Download & Patch")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSnapchatVersion != null,
                    onClick = {
                        navigation.navigateTo(LSPatchTab::class, args = Bundle().apply {
                            putParcelable("downloadItem", selectedSnapchatVersion)
                        }, noHistory = true)
                    }
                ) {
                    Text("Install/Restore Original Snapchat")
                }
            }
        }
    }
}