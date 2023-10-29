package me.rhunk.snapenhance.manager.ui.tab

import android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.BuildConfig
import me.rhunk.snapenhance.manager.lspatch.config.Constants
import me.rhunk.snapenhance.manager.ui.Tab
import me.rhunk.snapenhance.manager.ui.tab.download.SEDownloadTab
import me.rhunk.snapenhance.manager.ui.tab.download.SnapchatPatchTab

class HomeTab : Tab("home", true, icon = Icons.Default.Home) {
    override fun init(activity: ComponentActivity) {
        super.init(activity)
        registerNestedTab(SEDownloadTab::class)
        registerNestedTab(SnapchatPatchTab::class)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var snapchatAppInfo by remember { mutableStateOf(null as PackageInfo?) }
        var snapEnhanceInfo by remember { mutableStateOf(null as PackageInfo?) }

        Column {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Snapchat", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        snapchatAppInfo?.let {
                            Text(text = "${it.versionName} (${it.longVersionCode})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        snapchatAppInfo?.let { appInfo ->
                            val isLSPatched = appInfo.applicationInfo.appComponentFactory == Constants.PROXY_APP_COMPONENT_FACTORY
                            if (isLSPatched) {
                                Text(text = "Patched", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            OutlinedButton(onClick = {
                                navigation.navigateTo(SnapchatPatchTab::class)
                            }) {
                                Text(text = if (isLSPatched) "Repatch" else "Patch")
                            }
                        } ?: run {
                            Text(text = "Not installed", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navigation.navigateTo(SEDownloadTab::class)
                        }
                        .padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "SnapEnhance", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        snapEnhanceInfo?.let {
                            Text(text = "${it.versionName} (${it.longVersionCode}) - ${if ((it.applicationInfo.flags and FLAG_DEBUGGABLE) != 0) "Debug" else "Release"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        snapEnhanceInfo?.let {
                            Text(text = "Installed", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        } ?: run {
                            Text(text = "Not installed", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Icon(imageVector = Icons.AutoMirrored.Default.OpenInNew, contentDescription = null, Modifier.padding(10.dp))
                    }
                }
            }
        }

        SideEffect {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    snapchatAppInfo = runCatching {
                        context.packageManager.getPackageInfo(sharedConfig.snapchatPackageName ?: "com.snapchat.android", 0)
                    }.getOrNull()
                    snapEnhanceInfo = runCatching {
                        context.packageManager.getPackageInfo(sharedConfig.snapEnhancePackageName ?: BuildConfig.APPLICATION_ID, 0)
                    }.getOrNull()
                }
            }
        }
    }
}