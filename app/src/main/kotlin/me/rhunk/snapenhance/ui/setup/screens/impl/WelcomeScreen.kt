package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen

class WelcomeScreen : SetupScreen() {

    @Composable
    override fun Content() {
        Text(text = "Welcome")
        Button(onClick = { allowNext(true) }) {
            Text(text = "Next")
        }
    }
}