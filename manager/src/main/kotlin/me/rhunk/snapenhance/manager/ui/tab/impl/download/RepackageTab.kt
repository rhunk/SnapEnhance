package me.rhunk.snapenhance.manager.ui.tab.impl.download

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.manager.patch.Repackager
import me.rhunk.snapenhance.manager.ui.tab.Tab
import java.io.File

enum class RepackageState {
    IDLE,
    WORKING,
    SUCCESS,
    FAILED
}

class RepackageTab : Tab("repackage") {
    private var throwable: Throwable? = null

    private suspend fun repackage(apk: File, oldPackage: String, state: MutableState<RepackageState>) {
        state.value = RepackageState.WORKING
        val repackager = Repackager(activity, activity.externalCacheDirs.first(), sharedConfig.snapEnhancePackageName)

        runCatching {
            repackager.patch(apk)
        }.onFailure {
            throwable = it
            state.value = RepackageState.FAILED
            return
        }.onSuccess { originApk ->
            state.value = RepackageState.SUCCESS

            withContext(Dispatchers.Main) {
                navigation.navigateTo(InstallPackageTab::class, Bundle().apply {
                    putString("downloadPath", originApk.absolutePath)
                    putString("appPackage", oldPackage)
                    putBoolean("uninstall", true)
                }, noHistory = true)
            }

            return
        }
    }

    @Composable
    override fun Content() {
        val apkPath = remember { getArguments()?.getString("apkPath") } ?: return
        val oldPackage = remember { getArguments()?.getString("oldPackage") } ?: return
        val state = remember { mutableStateOf(RepackageState.IDLE) }

        LaunchedEffect(apkPath) {
            launch(Dispatchers.IO) {
                repackage(File(apkPath), oldPackage,  state)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            when (state.value) {
                RepackageState.WORKING -> Text(text = "Repackaging ...")
                RepackageState.FAILED -> {
                    Text(text = "Failed")
                    Text(text = (throwable?.localizedMessage + throwable?.stackTraceToString()))
                }
                else -> {}
            }
        }
    }
}