package me.rhunk.snapenhance.bridge.client

import android.os.Environment
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.AbstractBridgeClient
import me.rhunk.snapenhance.bridge.MessageLoggerWrapper
import me.rhunk.snapenhance.bridge.common.impl.file.BridgeFileType
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class RootBridgeClient : AbstractBridgeClient() {
    private lateinit var messageLoggerWrapper: MessageLoggerWrapper
    companion object {
        private val MOD_FOLDER = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"SnapEnhance")
    }

    override fun start(callback: (Boolean) -> Unit) {
        if (!MOD_FOLDER.exists()) {
            MOD_FOLDER.mkdirs()
        }
        messageLoggerWrapper = MessageLoggerWrapper(File(MOD_FOLDER, BridgeFileType.MESSAGE_LOGGER_DATABASE.fileName)).also { it.init() }
        callback(true)
    }

    override fun createAndReadFile(fileType: BridgeFileType, defaultContent: ByteArray): ByteArray {
        val file = File(MOD_FOLDER, fileType.fileName)
        if (file.exists()) {
            return readFile(fileType)
        }
        val outputStream = openFileWritable(file)
        outputStream.write(defaultContent)
        outputStream.close()
        return defaultContent
    }

    override fun readFile(fileType: BridgeFileType): ByteArray {
        return File(MOD_FOLDER, fileType.fileName).readBytes()
    }

    override fun writeFile(fileType: BridgeFileType, content: ByteArray?): Boolean {
        val outputStream = openFileWritable(File(MOD_FOLDER, fileType.fileName))
        outputStream.write(content)
        outputStream.close()
        return true
    }

    override fun deleteFile(fileType: BridgeFileType): Boolean {
        val file = File(MOD_FOLDER, fileType.fileName)
        val exists = file.exists()
        if (exists) {
            rootOperation("rm ${file.absolutePath}")
        }
        return exists
    }

    override fun isFileExists(fileType: BridgeFileType): Boolean {
        return File(MOD_FOLDER, fileType.fileName).exists()
    }

    override fun downloadContent(url: String, path: String): Boolean {
        return true
    }

    override fun getMessageLoggerMessage(id: Long): ByteArray? {
        val (state, messageData) = messageLoggerWrapper.getMessage(id)
        if (state) {
            return messageData
        }
        return null
    }

    override fun addMessageLoggerMessage(id: Long, message: ByteArray) {
        messageLoggerWrapper.addMessage(id, message)
    }

    override fun deleteMessageLoggerMessage(id: Long) {
        messageLoggerWrapper.deleteMessage(id)
    }

    override fun clearMessageLogger() {
        messageLoggerWrapper.clearMessages()
    }

    override fun fetchTranslations(): LocaleResult {
        val locale = "en_US"//Locale.getDefault().toString()

        //https://github.com/LSPosed/LSPosed/blob/master/core/src/main/java/org/lsposed/lspd/util/LspModuleClassLoader.java#L36
        val moduleApk = javaClass.classLoader.javaClass.declaredFields.first { it.type == String::class.java }.let {
            it.isAccessible = true
            it.get(javaClass.classLoader) as String
        }

        val langJsonData: ByteArray? = ZipInputStream(FileInputStream(moduleApk)).let { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "assets/lang/$locale.json") {
                    return@let zip.readBytes()
                }
            }
            return@let null
        }

        if (langJsonData != null) {
            Logger.debug("Fetched translations for $locale")
            return LocaleResult(locale, langJsonData)
        }

        throw Throwable("Failed to fetch translations for $locale")
    }

    private fun rootOperation(command: String): String {
        val process = Runtime.getRuntime().exec("su -c $command")
        process.waitFor()
        process.errorStream?.bufferedReader()?.let {
            val error = it.readText()
            if (error.isNotEmpty()) {
                throw Throwable("Failed to execute root operation: $error")
            }
        }
        Logger.debug("Root operation executed: $command")
        return process.inputStream.bufferedReader().readText()
    }

    private fun openFileWritable(file: File): OutputStream {
        runCatching {
            if (!file.exists()) rootOperation("touch ${file.absolutePath}")
        }.onFailure {
            Logger.error("Failed to set file permissions: ${it.message}")
        }

        return FileOutputStream(file)
    }
}