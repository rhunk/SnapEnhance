package me.rhunk.snapenhance.manager.sections

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rhunk.snapenhance.manager.Section
import me.rhunk.snapenhance.manager.data.InstallationSummary

class HomeSection : Section() {
    companion object {
        val cardMargin = 10.dp
    }

    @OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
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
                }, modifier = Modifier.weight(1f)
                    .align(Alignment.CenterVertically)
                )

                //inline button
                Button(onClick = {}, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }

    @Composable
    @Preview
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(ScrollState(0))
        ) {
            Text(
                "SnapEnhance",
                fontSize = 32.sp,
                modifier = Modifier.padding(32.dp)
            )

            Text(
                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies ultrices, nunc nisl aliquam nunc, quis aliquam nisl nunc eu nisl. Donec euismod, nisl eget ultricies ultrices, nunc nisl aliquam nunc, quis aliquam nisl nunc eu nisl.",
                modifier = Modifier.padding(16.dp)
            )

            SummaryCards(manager.getInstallationSummary())
        }
    }
}