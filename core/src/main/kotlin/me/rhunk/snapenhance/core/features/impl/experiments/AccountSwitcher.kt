package me.rhunk.snapenhance.core.features.impl.experiments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.FileType
import me.rhunk.snapenhance.common.ui.AppMaterialTheme
import me.rhunk.snapenhance.common.ui.createComposeAlertDialog
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.util.ktx.toParcelFileDescriptor
import me.rhunk.snapenhance.core.util.ktx.vibrateLongPress
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class AccountSwitcher: Feature("Account Switcher", loadParams = FeatureLoadParams.INIT_SYNC or FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private var activity: Activity? = null
    private var exportCallback: Pair<Int, String>? = null // requestCode -> userId
    private var importRequestCode: Int? = null

    private val accounts = mutableStateListOf<Pair<String, String>>()
    private val isLoginActivity get() = activity?.javaClass?.name?.endsWith("LoginSignupActivity") == true

    private fun updateUsers() {
        accounts.clear()
        runCatching {
            accounts.addAll(context.bridgeClient.getAccountStorage().accounts.map { it.key to it.value })
        }.onFailure {
            context.log.error("Failed to update users", it)
        }
    }

    @Composable
    private fun ManagementPopup() {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                updateUsers()
            }
        }


        Column(
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Account Switcher", modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 25.sp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    if (accounts.isEmpty()) {
                        Text("No accounts found! To start, backup your current account.", modifier = Modifier
                            .padding(16.dp)
                            .padding(16.dp)
                            .fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

                items(accounts) { user ->
                    var removeAccountPopup by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isLoginActivity && context.database.myUserId == user.first) MaterialTheme.colorScheme.surfaceBright
                            else MaterialTheme.colorScheme.surfaceDim
                        ) ,
                        onClick = {
                            runCatching {
                                if (!isLoginActivity && context.database.myUserId == user.first) {
                                    context.shortToast("Already logged in as ${user.second}")
                                    return@runCatching
                                }

                                if (!isLoginActivity && context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                                    backupCurrentAccount()
                                }

                                login(userId = user.first, username = user.second)
                            }.onFailure {
                                context.shortToast("Failed to login. Check logs for more info.")
                                context.log.error("Failed to login", it)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.second, modifier = Modifier
                                .padding(10.dp)
                                .weight(1f))
                            Row(
                                modifier = Modifier
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                FilledIconButton(onClick = {
                                    val requestCode = Random.nextInt(100, 65535)
                                    exportCallback = requestCode to user.first

                                    activity?.startActivityForResult(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_TITLE, "account_${user.second}.zip")
                                            },
                                            "Export account"
                                        ),
                                        requestCode
                                    )
                                }) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = "Export account")
                                }
                                FilledIconButton(onClick = {
                                    removeAccountPopup = true
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove account")
                                }
                            }
                        }
                    }

                    if (removeAccountPopup) {
                        AlertDialog(
                            onDismissRequest = { removeAccountPopup = false },
                            confirmButton = {
                                Button(onClick = {
                                    context.bridgeClient.getAccountStorage().removeAccount(user.first)
                                    removeAccountPopup = false
                                    updateUsers()
                                }) {
                                    Text("Remove")
                                }
                            },
                            title = { Text("Remove account") },
                            text = { Text("Are you sure you want to remove ${user.second}?") },
                            dismissButton = {
                                Button(onClick = {
                                    removeAccountPopup = false
                                }) {
                                    Text("Cancel")
                                }
                            },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        activity?.startActivityForResult(
                            Intent.createChooser(
                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/zip"
                                },
                                "Import account"
                            ),
                            Random.nextInt(100, 65535).also {
                                importRequestCode = it
                            }
                        )
                    }
                ) {
                    Text("Import account")
                }

                if (!isLoginActivity) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            backupCurrentAccount()
                            updateUsers()
                        }
                    ) {
                        Text("Backup current account")
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            if (context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                                backupCurrentAccount()
                            }
                            logout()
                        }
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
    }


    private fun showManagementPopup() {
        context.runOnUiThread {
            createComposeAlertDialog(activity!!) {
                AppMaterialTheme(isDarkTheme = true) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        ManagementPopup()
                    }
                }
            }.show()
        }
    }

    private fun logout() {
        context.androidContext.dataDir.resolve( "shared_prefs/user_session_shared_pref.xml").takeIf { it.exists() }?.delete()
        context.shortToast("Logged out")
        context.softRestartApp()
    }

    private fun login(userId: String, username: String) {
        val accountData = context.bridgeClient.getAccountStorage().getAccountData(userId)?.let { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
        if (accountData == null) {
            context.shortToast("Account data not found")
            return
        }

        arrayOf(
            context.androidContext.filesDir,
            context.androidContext.cacheDir,
            context.androidContext.dataDir.resolve("databases"),
        ).forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }

        val zipInputStream = ZipInputStream(accountData.inputStream())
        var entry: ZipEntry?
        while (zipInputStream.nextEntry.also { entry = it } != null) {
            val file = context.androidContext.dataDir.resolve(entry!!.name)
            if (file.exists()) {
                file.delete()
            } else {
                file.parentFile?.mkdirs()
            }
            context.log.debug("Extracting ${file.absolutePath}")
            file.outputStream().use {
                zipInputStream.copyTo(it)
            }
        }

        context.log.debug("Account data restored")
        context.shortToast("Logged in as $username")
        context.softRestartApp()
    }

    private fun getCurrentAccountData(): ParcelFileDescriptor {
        val pfd = ParcelFileDescriptor.createPipe()

        context.coroutineScope.launch(Dispatchers.IO) {
            val zipOutputStream = ZipOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pfd[1]))

            for (file in arrayOf(
                "databases/main.db",
                "databases/main.db-shm",
                "databases/main.db-wal",
                "databases/core.db",
                "databases/core.db-wal",
                "databases/core.db-shm",
                "shared_prefs/user_session_shared_pref.xml",
                "shared_prefs/user_device_identity_keys.xml",
                "shared_prefs/com.google.android.gms.appid.xml",
            )) {
                context.androidContext.dataDir.resolve(file).takeIf { it.exists() }?.inputStream()?.use {
                    context.log.verbose("Adding $file to zip")
                    zipOutputStream.putNextEntry(ZipEntry(file))
                    it.copyTo(zipOutputStream)
                    zipOutputStream.closeEntry()
                }
            }
            zipOutputStream.flush()
            zipOutputStream.close()
        }

        return pfd[0]
    }

    private fun backupCurrentAccount() {
        runCatching {
            context.bridgeClient.getAccountStorage().addAccount(
                context.database.myUserId,
                context.database.getFriendInfo(context.database.myUserId)?.mutableUsername ?: "Unknown username",
                getCurrentAccountData()
            )
            context.shortToast("Account backed up!")
        }.onFailure {
            context.shortToast("Failed to backup account. Check logs for more info.")
            context.log.error("Failed to backup account", it)
        }
    }

    private fun importAccount(fileUri: Uri) {
        var tempZip: File? = null
        var mainDbFile: File? = null
        var mainDbWalFile: File? = null
        var mainDbShmFile: File? = null

        runCatching {
            // copy zip file
            activity!!.contentResolver.openInputStream(fileUri)?.use { input ->
                val bufferedInputStream = input.buffered()
                val fileType = MediaDownloaderHelper.getFileType(bufferedInputStream)

                if (fileType != FileType.ZIP) {
                    throw Exception("Invalid file type")
                }

                context.androidContext.cacheDir.resolve(System.currentTimeMillis().toString()).also {
                    tempZip = it
                }.outputStream().use { output ->
                    bufferedInputStream.copyTo(output)
                }
            }

            context.log.verbose("Extracting account data")

            // extract main.db in cache
            tempZip?.inputStream().use { fileInputStream ->
                val zipInputStream = ZipInputStream(fileInputStream)
                var entry: ZipEntry?
                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    val fileName = entry?.name?.substringAfterLast('/') ?: continue
                    if (!fileName.startsWith("main.db")) continue

                    val file = context.androidContext.cacheDir.resolve(fileName)
                    context.log.verbose("Found ${entry!!.name} in zip file")

                    when (fileName) {
                        "main.db" -> mainDbFile = file
                        "main.db-wal" -> mainDbWalFile = file
                        "main.db-shm" -> mainDbShmFile = file
                    }

                    file.outputStream().use {
                        zipInputStream.copyTo(it)
                    }
                }
            }

            assert(mainDbFile != null) { "main.db not found in zip file" }

            SQLiteDatabase.openDatabase(mainDbFile!!.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {  sqliteDatabase ->
                val userId = sqliteDatabase.rawQuery("SELECT userId FROM SnapToken", null).use {
                    if (!it.moveToFirst()) throw Exception("userId not found in main.db")
                    it.getString(0)
                }
                context.log.verbose("Found userId $userId")
                val username = sqliteDatabase.rawQuery("SELECT username FROM Friend WHERE userId = ?", arrayOf(userId)).use {
                    if (!it.moveToFirst()) throw Exception("username not found in main.db")
                    it.getString(0)
                }
                context.log.verbose("Found username $username")
                tempZip?.inputStream()?.use {
                    context.bridgeClient.getAccountStorage().addAccount(
                        userId,
                        username,
                        it.toParcelFileDescriptor(context.coroutineScope)
                    )
                }
                context.shortToast("Imported $username!")
                updateUsers()
            }
        }.onFailure {
            context.shortToast("Failed to import account: ${it.message}")
            context.log.error("Failed to import account", it)
        }

        tempZip?.delete()
        mainDbFile?.delete()
        mainDbWalFile?.delete()
        mainDbShmFile?.delete()
    }

    private fun hookActivityResult(activityClass: Class<*>) {
        activityClass.hook("onActivityResult", HookStage.BEFORE) { param ->
            val requestCode = param.arg<Int>(0)
            val resultCode = param.arg<Int>(1)
            val intent = param.arg<Intent>(2)

            if (importRequestCode == requestCode) {
                importRequestCode = null
                if (resultCode != Activity.RESULT_OK) return@hook
                val uri = intent.data ?: return@hook

                context.coroutineScope.launch { importAccount(uri) }
            }

            if (exportCallback?.first == requestCode) {
                val userId = exportCallback?.second
                exportCallback = null
                param.setResult(null)
                if (resultCode != Activity.RESULT_OK) return@hook

                context.coroutineScope.launch {
                    runCatching {
                        intent.data?.let { uri ->
                            val accountDataPfd = context.bridgeClient.getAccountStorage().getAccountData(userId) ?: throw Exception("Account data not found")
                            context.androidContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                ParcelFileDescriptor.AutoCloseInputStream(accountDataPfd).use {
                                    it.copyTo(outputStream)
                                }
                            }
                            context.shortToast("Account exported!")
                        }
                    }.onFailure {
                        context.shortToast("Failed to export account. Check logs for more info.")
                        context.log.error("Failed to export account", it)
                    }
                }
            }
        }
    }

    override fun onActivityCreate() {
        if (context.config.experimental.accountSwitcher.globalState != true) return

        activity = context.mainActivity!!
        hookActivityResult(activity!!::class.java)
        val hovaHeaderSearchIcon = activity!!.resources.getId("hova_header_search_icon")

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view.id != hovaHeaderSearchIcon) return@subscribe

            event.view.setOnLongClickListener {
                activity!!.vibrateLongPress()
                showManagementPopup()
                false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun init() {
        if (context.config.experimental.accountSwitcher.globalState != true) return

        findClass("com.snap.identity.service.ForcedLogoutBroadcastReceiver").hook("onReceive", HookStage.BEFORE) { param ->
            val intent = param.arg<Intent>(1)
            if (isLoginActivity) return@hook
            if (intent.getBooleanExtra("forced", false) && !context.config.experimental.preventForcedLogout.get()) {
                runCatching {
                    val accountStorage = context.bridgeClient.getAccountStorage()

                    if (accountStorage.isAccountExists(context.database.myUserId)) {
                        accountStorage.removeAccount(context.database.myUserId)
                        context.shortToast("Removed account due to forced logout")
                    }
                }
                return@hook
            }

            if (context.config.experimental.accountSwitcher.autoBackupCurrentAccount.get()) {
                backupCurrentAccount()
            }
        }

        findClass("com.snap.identity.loginsignup.ui.LoginSignupActivity").apply {
            hookActivityResult(this)
            hook("onCreate", HookStage.AFTER) { param ->
                activity = param.thisObject()
                activity!!.findViewById<FrameLayout>(android.R.id.content).addView(Button(activity).apply {
                    text = "Switch Account"
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                        setMargins(32, 32, 0, 0)
                    }
                    setOnClickListener {
                        showManagementPopup()
                    }
                })
            }
        }
    }
}