package me.rhunk.snapenhance.manager.setup.screens.impl

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rhunk.snapenhance.manager.setup.screens.SetupScreen

class SaveFolderScreen : SetupScreen() {

    @Composable
    override fun Content() {
        Text(text = "SaveFolder")
        Button(onClick = {allowNext(true)}) {
            Text(text = "Next")
        }
    }
}