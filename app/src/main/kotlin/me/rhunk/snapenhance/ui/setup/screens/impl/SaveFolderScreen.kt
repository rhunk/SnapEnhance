package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ui.util.ObservableMutableState
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import me.rhunk.snapenhance.ui.util.ChooseFolderHelper

class SaveFolderScreen : SetupScreen() {
    private lateinit var saveFolder: ObservableMutableState<String>
    private lateinit var openFolderLauncher: () -> Unit

    override fun init() {
        saveFolder = ObservableMutableState(
                defaultValue = "",
                onChange = { _, newValue ->
                    Logger.debug(newValue)
                    if (newValue.isNotBlank()) {
                        context.config.root.downloader.saveFolder.set(newValue)
                        context.config.writeConfig()
                        allowNext(true)
                    }
                }
            )
        openFolderLauncher = ChooseFolderHelper.createChooseFolder(context.activity as ComponentActivity) { uri ->
            saveFolder.value = uri
        }
    }

    @Composable
    override fun Content() {
        DialogText(text = context.translation["setup.dialogs.save_folder"])
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            openFolderLauncher()
        }) {
            Text(text = context.translation["setup.dialogs.select_save_folder_button"])
        }
    }
}