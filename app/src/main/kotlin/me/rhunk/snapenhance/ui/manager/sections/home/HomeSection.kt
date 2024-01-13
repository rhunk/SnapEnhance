package me.rhunk.snapenhance.ui.manager.sections.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.common.BuildConfig
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.manager.data.Updater
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
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
    }

    override fun onResumed() {
        if (!context.mappings.isMappingsLoaded) {
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
                if (!BuildConfig.DEBUG) {
                    latestUpdate = Updater.checkForLatestRelease()
                }
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
                    homeSubSection.LogsTopBarButtons(activityLauncherHelper, navController, this)
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
        val avenirNextFontFamily = remember {
            FontFamily(
                Font(R.font.avenir_next_medium, FontWeight.Medium)
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(ScrollState(0))
        ) {

            Image(
                painter = painterResource(id = R.drawable.launcher_icon_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                contentScale = ContentScale.FillHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(1.8f)
                    .height(90.dp)
            )

            Text(
                text = remember { intArrayOf(101,99,110,97,104,110,69,112,97,110,83).map { it.toChar() }.joinToString("").reversed() },
                fontSize = 30.sp,
                fontFamily = avenirNextFontFamily,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Text(
                text = "v" + BuildConfig.VERSION_NAME + " \u00b7 by rhunk",
                fontSize = 12.sp,
                fontFamily = avenirNextFontFamily,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Text(
                text = "An xposed module made to enhance your Snapchat experience",
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).clickable {
                        context.activity?.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(
                                    intArrayOf(101,99,110,97,104,110,69,112,97,110,83,47,107,110,117,104,114,47,109,111,99,46,98,117,104,116,105,103,47,47,58,115,112,116,116,104).map { it.toChar() }.joinToString("").reversed()
                                )
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).clickable {
                        context.activity?.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(
                                    intArrayOf(101,99,110,97,104,110,101,112,97,110,115,47,101,109,46,116,47,47,58,115,112,116,116,104).map { it.toChar() }.joinToString("").reversed()
                                )
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

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

            SummaryCards(installationSummary = installationSummary ?: return)
        }
    }
}