package me.rhunk.snapenhance.ui.setup.screens

import androidx.compose.runtime.Composable

abstract class SetupScreen {
    lateinit var allowNext: (Boolean) -> Unit
    lateinit var route: String

    @Composable
    abstract fun Content()
}