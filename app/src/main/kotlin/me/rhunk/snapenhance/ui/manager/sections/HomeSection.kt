package me.rhunk.snapenhance.ui.manager.sections

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.manager.data.InstallationSummary
import me.rhunk.snapenhance.ui.setup.Requirements
import java.util.Locale

class HomeSection : Section() {
    companion object {
        val cardMargin = 10.dp
    }
    private val installationSummary = mutableStateOf(null as InstallationSummary?)
    private val userLocale = mutableStateOf(null as String?)

    @Composable
    private fun SummaryCards(installationSummary: InstallationSummary) {
        //installation summary
        OutlinedCard(
            modifier = Modifier
                .padding(all = cardMargin)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(all = 16.dp)) {
                if (installationSummary.snapchatInfo != null) {
                    Text("Snapchat version: ${installationSummary.snapchatInfo.version}")
                    Text("Snapchat version code: ${installationSummary.snapchatInfo.versionCode}")
                } else {
                    Text("Snapchat not installed/detected")
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .padding(all = cardMargin)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = "Mappings",
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .align(Alignment.CenterVertically)
                )

                Text(text = if (installationSummary.mappingsInfo == null || installationSummary.mappingsInfo.isOutdated) {
                    "Mappings ${if (installationSummary.mappingsInfo == null) "not generated" else "outdated"}"
                } else {
                    "Mappings version ${installationSummary.mappingsInfo.generatedSnapchatVersion}"
                }, modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                )

                //inline button
                Button(onClick = {
                     context.checkForRequirements(Requirements.MAPPINGS)
                }, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
        OutlinedCard(
            modifier = Modifier
                .padding(all = cardMargin)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 16.dp),
            ) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = "Language",
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .align(Alignment.CenterVertically)
                )
                Text(text = userLocale.value ?: "Unknown", modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                )

                //inline button
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
        installationSummary.value = context.getInstallationSummary()
        userLocale.value = context.translation.loadedLocale.getDisplayName(Locale.getDefault())
    }

    override fun sectionTopBarName() = "SnapEnhance"

    @Composable
    @Preview
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(ScrollState(0))
        ) {
            Text(
                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies ultrices, nunc nisl aliquam nunc, quis aliquam nisl nunc eu nisl. Donec euismod, nisl eget ultricies ultrices, nunc nisl aliquam nunc, quis aliquam nisl nunc eu nisl.",
                modifier = Modifier.padding(16.dp)
            )

            SummaryCards(installationSummary = installationSummary.value ?: return)
        }
    }
}