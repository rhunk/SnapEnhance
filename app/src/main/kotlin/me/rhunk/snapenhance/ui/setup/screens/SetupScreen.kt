package me.rhunk.snapenhance.ui.setup.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rhunk.snapenhance.RemoteSideContext

abstract class SetupScreen {
    lateinit var context: RemoteSideContext
    lateinit var allowNext: (Boolean) -> Unit
    lateinit var route: String

    @Composable
    fun DialogText(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(16.dp).then(modifier)
        )
    }

    open fun init() {}
    open fun onLeave() {}

    @Composable
    abstract fun Content()
}