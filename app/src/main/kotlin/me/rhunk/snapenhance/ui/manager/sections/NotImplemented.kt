package me.rhunk.snapenhance.ui.manager.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rhunk.snapenhance.ui.manager.Section

class NotImplemented : Section() {
    @Composable
    override fun Content() {
        Column {
            Text(text = "Not implemented yet!")
        }
    }
}