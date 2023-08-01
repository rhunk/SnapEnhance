package me.rhunk.snapenhance.manager.setup.screens.impl

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rhunk.snapenhance.manager.setup.screens.SetupScreen

class MappingsScreen : SetupScreen() {
    @Composable
    override fun Content() {
        Text(text = "Mappings")
        Button(onClick = { allowNext(true) }) {
            Text(text = "Next")
        }
    }
}