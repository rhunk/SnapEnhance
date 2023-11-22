package me.rhunk.snapenhance.manager.ui.tab.impl.download

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.data.download.InstallStage
import me.rhunk.snapenhance.manager.ui.tab.Tab
import me.rhunk.snapenhance.manager.ui.tab.impl.HomeTab
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File


class InstallPackageTab : Tab("install_app") {
    private lateinit var installPackageIntentLauncher: ActivityResultLauncher<Intent>
    private lateinit var uninstallPackageIntentLauncher: ActivityResultLauncher<Intent>
    private var uninstallPackageCallback: ((resultCode: Int) -> Unit)? = null
    private var installPackageCallback: ((resultCode: Int) -> Unit)? = null

    private val hasRoot get() = sharedConfig.useRootInstaller

    override fun init(activity: ComponentActivity) {
        super.init(activity)
        installPackageIntentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            installPackageCallback?.invoke(it.resultCode)
        }
        uninstallPackageIntentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            uninstallPackageCallback?.invoke(it.resultCode)
        }
    }

    private fun downloadArtifact(url: String, progress: (Float) -> Unit): File? {
        val urlScheme = Uri.parse(url).scheme
        if (urlScheme != "https" && urlScheme != "http") {
            val file = File(url)
            val dest = File(activity.externalCacheDirs.first(), file.name).also {
                it.deleteOnExit()
            }
            if (dest.exists()) return file
            file.copyTo(dest)
            return dest
        }

        val endpoint = Request.Builder().url(url).build()
        val response = OkHttpClient().newCall(endpoint).execute()
        if (!response.isSuccessful) throw Throwable("Failed to download artifact: ${response.code}")

        return response.body.byteStream().use { input ->
            val file = File.createTempFile("artifact", ".apk", activity.externalCacheDirs.first()).also {
                it.deleteOnExit()
            }
            runCatching {
                file.outputStream().use { output ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    var totalRead = 0L
                    val totalSize = response.body.contentLength()
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        progress(totalRead.toFloat() / totalSize.toFloat())
                    }
                }
                file
            }.getOrNull()
        }
    }


    @Composable
    @Suppress("DEPRECATION")
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        var installStage by remember { mutableStateOf(InstallStage.DOWNLOADING) }
        var downloadProgress by remember { mutableFloatStateOf(-1f) }
        var downloadedFile by remember { mutableStateOf<File?>(null) }

        LaunchedEffect(Unit) {
            uninstallPackageCallback = null
            installPackageCallback = null
        }

        val downloadPath = remember { getArguments()?.getString("downloadPath") } ?: return
        val appPackage = remember { getArguments()?.getString("appPackage") } ?: return
        val shouldUninstall = remember { getArguments()?.getBoolean("uninstall")?.let {
            if (runCatching { activity.packageManager.getPackageInfo(appPackage, 0) }.getOrNull() == null) {
                false
            } else it
        } ?: false }

        BackHandler(installStage != InstallStage.DONE || installStage != InstallStage.ERROR) {}
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (installStage != InstallStage.DONE && installStage != InstallStage.ERROR) {
                    CircularProgressIndicator()
                }
            }

            when (installStage) {
                InstallStage.DOWNLOADING -> {
                    Text(text = "Downloading ...")
                    LinearProgressIndicator(progress = downloadProgress, Modifier.fillMaxWidth().height(4.dp), strokeCap = StrokeCap.Round)
                }
                InstallStage.UNINSTALLING -> {
                    Text(text = "Uninstalling app $appPackage...")
                }
                InstallStage.INSTALLING -> {
                    Text(text = "Installing ...")
                }
                InstallStage.DONE -> {
                    LaunchedEffect(Unit) {
                        navigation.navigateTo(HomeTab::class, noHistory = true)
                        Toast.makeText(context, "Successfully installed $appPackage!", Toast.LENGTH_SHORT).show()
                    }
                }
                InstallStage.ERROR -> Text(text = "Failed to install $appPackage. Check logcat for more details.")
            }
        }

        fun uninstallPackageRoot(): Boolean {
            val result = Shell.su("pm uninstall $appPackage").exec()
            if (result.isSuccess) {
                return true
            }
            toast("Root uninstall failed: ${result.out}")
            return false
        }

        fun installPackageRoot(): Boolean {
            val result = Shell.su(
                "cp \"${downloadedFile!!.absolutePath}\" /data/local/tmp/",
                "pm install -r \"/data/local/tmp/${downloadedFile!!.name}\"",
                "rm /data/local/tmp/${downloadedFile!!.name}"
            ).exec()
            if (result.isSuccess) {
                installStage = InstallStage.DONE
                return true
            }
            toast("Root install failed: ${result.out}")
            return false
        }

        fun installPackage() {
            installStage = InstallStage.INSTALLING
            if (hasRoot && installPackageRoot()) {
                downloadedFile?.delete()
                return
            }
            installPackageCallback = resultCallbacks@{ code ->
                installStage = if (code != ComponentActivity.RESULT_OK) {
                    InstallStage.ERROR
                } else {
                    InstallStage.DONE
                }
                downloadedFile?.delete()
            }

            installPackageIntentLauncher.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = FileProvider.getUriForFile(context, "me.rhunk.snapenhance.manager.provider", downloadedFile!!)
                setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            })
        }


        LaunchedEffect(Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    downloadedFile = downloadArtifact(downloadPath) { downloadProgress = it } ?: run {
                        installStage = InstallStage.ERROR
                        return@launch
                    }
                    if (shouldUninstall) {
                        installStage = InstallStage.UNINSTALLING
                        if (hasRoot && uninstallPackageRoot()) {
                            installPackage()
                            return@launch
                        }
                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                            data = "package:$appPackage".toUri()
                            putExtra(Intent.EXTRA_RETURN_RESULT, true)
                        }
                        uninstallPackageCallback = resultCallback@{ resultCode ->
                            if (resultCode != ComponentActivity.RESULT_OK) {
                                installStage = InstallStage.ERROR
                                downloadedFile?.delete()
                                return@resultCallback
                            }
                            installPackage()
                        }
                        uninstallPackageIntentLauncher.launch(intent)
                    } else {
                        installPackage()
                    }
                }.onFailure {
                    it.printStackTrace()
                    installStage = InstallStage.ERROR
                    downloadedFile?.delete()
                }
            }
        }
    }
}
