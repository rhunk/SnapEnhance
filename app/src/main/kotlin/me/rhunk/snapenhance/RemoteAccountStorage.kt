package me.rhunk.snapenhance

import android.os.ParcelFileDescriptor
import me.rhunk.snapenhance.bridge.AccountStorage
import me.rhunk.snapenhance.core.util.ktx.toParcelFileDescriptor

class RemoteAccountStorage(
    private val context: RemoteSideContext
): AccountStorage.Stub() {
    private val accountFolder = context.androidContext.filesDir.resolve("accounts").also {
        if (!it.exists()) it.mkdirs()
    }

    override fun getAccounts(): Map<String, String> {
        return accountFolder.listFiles()?.sortedByDescending { it.lastModified() }?.mapNotNull { file ->
            if (!file.name.endsWith(".zip") || !file.name.contains("|")) return@mapNotNull null
            file.nameWithoutExtension.split('|').let { it[0] to it[1] }
        }?.toMap() ?: emptyMap()
    }

    override fun addAccount(userId: String, username: String, pfd: ParcelFileDescriptor) {
        removeAccount(userId)
        accountFolder.resolve("$userId|$username.zip").outputStream().use { fileOutputStream ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use {
                it.copyTo(fileOutputStream)
            }
        }
    }

    override fun removeAccount(userId: String) {
        accountFolder.listFiles()?.firstOrNull {
            it.nameWithoutExtension.startsWith(userId)
        }?.also {
            context.log.verbose("Removing account file: ${it.name}")
            it.delete()
        }
    }

    override fun isAccountExists(userId: String): Boolean {
        return accountFolder.listFiles()?.any {
            it.nameWithoutExtension.startsWith(userId)
        } ?: false
    }

    override fun getAccountData(userId: String): ParcelFileDescriptor? {
        return accountFolder.listFiles()?.firstOrNull {
            it.nameWithoutExtension.startsWith(userId)
        }?.inputStream()?.toParcelFileDescriptor(context.coroutineScope)
    }
}