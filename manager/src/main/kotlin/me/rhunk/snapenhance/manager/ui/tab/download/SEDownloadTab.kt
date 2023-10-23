package me.rhunk.snapenhance.manager.ui.tab.download

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.BuildConfig
import me.rhunk.snapenhance.manager.data.download.SEArtifact
import me.rhunk.snapenhance.manager.data.download.SEVersion
import me.rhunk.snapenhance.manager.ui.Tab
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale


class SEDownloadTab : Tab("se_downloads") {
    private fun fetchSEReleases(): List<SEVersion>? {
        return runCatching {
            val endpoint =
                Request.Builder().url("https://api.github.com/repos/rhunk/SnapEnhance/releases")
                    .build()
            val response = OkHttpClient().newCall(endpoint).execute()
            if (!response.isSuccessful) return null

            val releases = JsonParser.parseString(response.body.string()).asJsonArray.also {
                if (it.size() == 0) return null
            }
            val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())

            releases.map { releaseObject ->
                val release = releaseObject.asJsonObject
                val versionName = release.getAsJsonPrimitive("tag_name").asString
                val releaseDate = release.getAsJsonPrimitive("published_at").asString.let { time ->
                    isoDateFormat.parse(time)?.let { date ->
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
                    } ?: time
                }
                val downloadAssets = release.getAsJsonArray("assets").associate { asset ->
                    val assetObject = asset.asJsonObject
                    SEArtifact(
                        fileName = assetObject.getAsJsonPrimitive("name").asString,
                        size = assetObject.getAsJsonPrimitive("size").asLong,
                        downloadUrl = assetObject.getAsJsonPrimitive("browser_download_url").asString
                    ).let { it.fileName to it }
                }
                SEVersion(versionName, releaseDate, downloadAssets)
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    override fun init(activity: ComponentActivity) {
        super.init(activity)
        registerNestedTab(InstallAppTab::class)
    }

    @Composable
    private fun DowngradeNoticeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Downgrade Notice", fontSize = 24.sp)
                Text(text = "You are about to update the app. If you're installing an older version over a newer one, make sure you have CorePatch installed. Otherwise, you will need to uninstall and install.", fontSize = 12.sp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
                Button(onClick = onSuccess) {
                    Text(text = "Continue")
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val snapEnhanceReleases = remember {
            mutableStateOf(null as List<SEVersion>?)
        }

        var selectedVersion by remember { mutableStateOf(null as SEVersion?) }
        var selectedArtifact by remember { mutableStateOf(null as SEArtifact?) }
        val appPackageName = remember { sharedConfig.snapEnhancePackageName ?: BuildConfig.APPLICATION_ID }
        val isAppInstalled = remember { runCatching { activity.packageManager.getPackageInfo(appPackageName, 0) != null }.getOrNull() != null }

        var showDowngradeNotice by remember { mutableStateOf(false) }

        fun triggerPackageInstallation(shouldUninstall: Boolean) {
            navigation.navigateTo(InstallAppTab::class, Bundle().apply {
                putParcelable("artifact", selectedArtifact)
                putString(
                    "appPackage",
                    sharedConfig.snapEnhancePackageName ?: BuildConfig.APPLICATION_ID
                )
                putBoolean("uninstall", shouldUninstall)
            })
        }

        if (showDowngradeNotice) {
            Dialog(onDismissRequest = { showDowngradeNotice = false }) {
                DowngradeNoticeDialog(onDismiss = { showDowngradeNotice = false }, onSuccess = {
                    triggerPackageInstallation(false)
                })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "SnapEnhance Builds")

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    if (snapEnhanceReleases.value == null) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                items(snapEnhanceReleases.value ?: listOf()) { version ->
                    OutlinedCard(
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clickable {
                                selectedArtifact =
                                    if (selectedVersion != version) null else selectedArtifact
                                selectedVersion = if (selectedVersion == version) null else version
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = version.versionName, fontSize = 24.sp)
                                Text(text = "Release ${version.releaseDate}", fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "${version.downloadAssets.size} assets", fontSize = 12.sp)
                            }
                        }
                    }

                    selectedVersion?.takeIf { it == version }?.let { selVersion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            selVersion.downloadAssets.values.forEach { artifact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            shape = MaterialTheme.shapes.medium,
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        .clickable {
                                            selectedArtifact =
                                                if (selectedArtifact == artifact) null else artifact
                                        }
                                        .background(
                                            if (selectedArtifact == artifact) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Android, contentDescription = null, modifier = Modifier.padding(start = 2.dp, end = 2.dp))
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 13.dp)
                                    ) {
                                        Text(text = artifact.fileName, fontSize = 15.sp)
                                        Text(
                                            text = "${artifact.size / 1024 / 1024} MB",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAppInstalled) {
                    Button(
                        onClick = {
                            triggerPackageInstallation(true)
                        },
                        enabled = selectedVersion != null && selectedArtifact != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Uninstall & Install")
                    }
                }
                Button(
                    onClick = {
                        if (isAppInstalled) {
                            showDowngradeNotice = true
                        } else {
                            triggerPackageInstallation(false)
                        }
                    },
                    enabled = selectedVersion != null && selectedArtifact != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (isAppInstalled) "Update" else "Install")
                }
            }
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                snapEnhanceReleases.value = fetchSEReleases()
            }
        }
    }
}