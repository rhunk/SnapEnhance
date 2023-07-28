package me.rhunk.snapenhance.manager.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rhunk.snapenhance.manager.Section

class FeaturesSection : Section() {
    @Composable
    @Preview
    override fun Content() {
        Column {
            Text(
                text = "Features",
                modifier = Modifier.padding(all = 15.dp),
                fontSize = 20.sp
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                items(100) { index ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 10.dp)
                            .height(70.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "Feature $index", modifier = Modifier.padding(all = 15.dp))
                        }
                    }
                }
            }
        }
    }
}