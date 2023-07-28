package me.rhunk.snapenhance.manager.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.rhunk.snapenhance.manager.Section

class NotImplemented : Section() {
    @Composable
    override fun Content() {
        Column {
            Text(text = "Not implemented yet!")
        }
    }
}