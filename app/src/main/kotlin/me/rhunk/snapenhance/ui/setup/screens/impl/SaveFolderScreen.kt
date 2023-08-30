package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.ObservableMutableState
import me.rhunk.snapenhance.ui.util.chooseFolder

class SaveFolderScreen : SetupScreen() {
    private lateinit var saveFolder: ObservableMutableState<String>

    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
        saveFolder = ObservableMutableState(
                defaultValue = "",
                onChange = { _, newValue ->
                    if (newValue.isNotBlank()) {
                        context.config.root.downloader.saveFolder.set(newValue)
                        context.config.writeConfig()
                        allowNext(true)
                    }
                }
            )
    }

    @Composable
    override fun Content() {
        DialogText(text = context.translation["setup.dialogs.save_folder"])
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            activityLauncherHelper.chooseFolder {
                saveFolder.value = it
            }
        }) {
            Text(text = context.translation["setup.dialogs.select_save_folder_button"])
        }
    }
}