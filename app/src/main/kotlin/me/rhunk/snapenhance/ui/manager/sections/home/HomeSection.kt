package me.rhunk.snapenhance.ui.manager.sections.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.Updater
import me.rhunk.snapenhance.ui.setup.Requirements
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.saveFile
import java.util.Locale

class HomeSection : Section() {
    companion object {
        val cardMargin = 10.dp
        const val HOME_ROOT = "home_root"
        const val LOGS_SECTION_ROUTE = "home_logs"
        const val SETTINGS_SECTION_ROUTE = "home_settings"
    }

    private var installationSummary: InstallationSummary? = null
    private var userLocale: String? = null
    private val homeSubSection by lazy { HomeSubSection(context) }
    private var latestUpdate: Updater.LatestRelease? = null
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @Composable
    private fun SummaryCardRow(icon: ImageVector? = null, title: String, action: @Composable () -> Unit) {
        Row(
            modifier = Modifier.padding(all = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .align(Alignment.CenterVertically)
                )
            }
            Text(text = title, modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
            )
            Column {
                action()
            }
        }
    }

    @Composable
    private fun SummaryCards(installationSummary: InstallationSummary) {
        val summaryInfo = remember {
            mapOf(
                "Build Issuer" to (installationSummary.modInfo?.buildIssuer ?: "Unknown"),
                "Build Type" to (if (installationSummary.modInfo?.isDebugBuild == true) "debug" else "release"),
                "Build Version" to (installationSummary.modInfo?.buildVersion ?: "Unknown"),
                "Build Package" to (installationSummary.modInfo?.buildPackageName ?: "Unknown"),
                "Activity Package" to (installationSummary.modInfo?.loaderPackageName ?: "Unknown"),
                "Device" to installationSummary.platformInfo.device,
                "Android Version" to installationSummary.platformInfo.androidVersion,
                "System ABI" to installationSummary.platformInfo.systemAbi
            )
        }

        Card(
            modifier = Modifier
                .padding(all = cardMargin)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp),
            ) {
                summaryInfo.forEach { (title, value) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 5.dp),
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            fontSize = 14.sp,
                            text = value,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .padding(all = cardMargin)
                .fillMaxWidth()
        ) {
            SummaryCardRow(
                icon = Icons.Filled.Map,
                title = if (installationSummary.modInfo == null || installationSummary.modInfo.mappingsOutdated == true) {
                    "Mappings ${if (installationSummary.modInfo == null) "not generated" else "outdated"}"
                } else {
                    "Mappings are up-to-date"
                }
            ) {
                Button(onClick = {
                    context.checkForRequirements(Requirements.MAPPINGS)
                }, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }

            SummaryCardRow(icon = Icons.Filled.Language, title = userLocale ?: "Unknown") {
                Button(onClick = {
                    context.checkForRequirements(Requirements.LANGUAGE)
                }, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                }
            }
        }
    }

    override fun onResumed() {
        if (!context.mappings.isMappingsLoaded()) {
            context.mappings.init(context.androidContext)
        }
        context.coroutineScope.launch {
            userLocale = context.translation.loadedLocale.getDisplayName(Locale.getDefault())
            runCatching {
                installationSummary = context.installationSummary
            }.onFailure {
                context.longToast("SnapEnhance failed to load installation summary: ${it.message}")
            }
            runCatching {
                latestUpdate = Updater.checkForLatestRelease()
            }.onFailure {
                context.longToast("SnapEnhance failed to check for updates: ${it.message}")
            }
        }
    }

    override fun sectionTopBarName(): String {
        if (currentRoute == HOME_ROOT) {
            return ""
        }
        return context.translation["manager.routes.$currentRoute"]
    }

    @Composable
    override fun FloatingActionButton() {
        if (currentRoute == LOGS_SECTION_ROUTE) {
            homeSubSection.LogsActionButtons()
        }
    }

    @Composable
    override fun TopBarActions(rowScope: RowScope) {
        rowScope.apply {
            when (currentRoute) {
                HOME_ROOT -> {
                    IconButton(onClick = {
                        navController.navigate(LOGS_SECTION_ROUTE)
                    }) {
                        Icon(Icons.Filled.BugReport, contentDescription = null)
                    }
                    IconButton(onClick = {
                        navController.navigate(SETTINGS_SECTION_ROUTE)
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                }
                LOGS_SECTION_ROUTE -> {
                    var showDropDown by remember { mutableStateOf(false) }

                    IconButton(onClick = {
                        showDropDown = true
                    }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showDropDown,
                        onDismissRequest = { showDropDown = false },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        DropdownMenuItem(onClick = {
                            context.log.clearLogs()
                            navController.navigate(LOGS_SECTION_ROUTE)
                            showDropDown = false
                        }, text = {
                            Text(
                                text = context.translation["manager.sections.home.logs.clear_logs_button"]
                            )
                        })

                        DropdownMenuItem(onClick = {
                            activityLauncherHelper.saveFile("snapenhance-logs-${System.currentTimeMillis()}.zip", "application/zip") { uri ->
                                context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                                    runCatching {
                                        context.log.exportLogsToZip(it)
                                        context.longToast("Saved logs to $uri")
                                    }.onFailure {
                                        context.longToast("Failed to save logs to $uri!")
                                        context.log.error("Failed to save logs to $uri!", it)
                                    }
                                }
                            }
                            showDropDown = false
                        }, text = {
                            Text(
                                text = context.translation["manager.sections.home.logs.export_logs_button"]
                            )
                        })
                    }
                }
            }
        }
    }

    override fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.navigation(
            route = enumSection.route,
            startDestination = HOME_ROOT
        ) {
            composable(HOME_ROOT) {
                Content()
            }
            composable(LOGS_SECTION_ROUTE) {
                homeSubSection.LogsSection()
            }
            composable(SETTINGS_SECTION_ROUTE) {
                SettingsSection(activityLauncherHelper).also { it.context = context }.Content()
            }
        }
    }


    @Composable
    @Preview
    override fun Content() {
        Column(
            modifier = Modifier
                .verticalScroll(ScrollState(0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.launcher_icon_monochrome),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .height(120.dp)
                        .scale(1.75f)
                )
                Text(
                    text = ("\u0065" + "\u0063" + "\u006e" + "\u0061" + "\u0068" + "\u006e" + "\u0045" + "\u0070" + "\u0061" + "\u006e" + "\u0053").reversed(),
                    fontSize = 30.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (latestUpdate != null) {
                OutlinedCard(
                    modifier = Modifier
                        .padding(all = cardMargin)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ){
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "SnapEnhance Update",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                fontSize = 12.sp,
                                text = "Version ${latestUpdate?.versionName} is available!",
                                lineHeight = 20.sp
                            )
                        }
                        Button(onClick = {
                            context.activity?.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(latestUpdate?.releaseUrl)
                                }
                            )
                        }, modifier = Modifier.height(40.dp)) {
                            Text(text = "Download")
                        }
                    }
                }
            }

            Text(
                text = "An Xposed module made to enhance your Snapchat experience",
                modifier = Modifier.padding(16.dp)
            )

            SummaryCards(installationSummary = installationSummary ?: return)
        }
    }
}