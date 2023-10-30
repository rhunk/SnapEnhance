package me.rhunk.snapenhance.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun ConfirmationDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { onDismiss() }) {
                    Text("Cancel")
                }
                Button(onClick = { onConfirm() }) {
                    Text("Yes")
                }
            }
        }
    }
}


@Composable
fun DowngradeNoticeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
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