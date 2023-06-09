package me.rhunk.snapenhance.bridge

import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.common.impl.file.BridgeFileType
import me.rhunk.snapenhance.bridge.common.impl.locale.LocaleResult

abstract class AbstractBridgeClient {
    lateinit var context: ModContext

    /**
     * Start the bridge client
     *
     * @param callback the callback to call when the initialization is done
     */
    abstract fun start(callback: (Boolean) -> Unit = {})

    /**
     * Create a file if it doesn't exist, and read it
     *
     * @param fileType       the type of file to create and read
     * @param defaultContent the default content to write to the file if it doesn't exist
     * @return the content of the file
     */
    abstract fun createAndReadFile(fileType: BridgeFileType, defaultContent: ByteArray): ByteArray

    /**
     * Read a file
     *
     * @param fileType the type of file to read
     * @return the content of the file
     */
    abstract fun readFile(fileType: BridgeFileType): ByteArray

    /**
     * Write a file
     *
     * @param fileType the type of file to write
     * @param content  the content to write to the file
     * @return true if the file was written successfully
     */
    abstract fun writeFile(fileType: BridgeFileType, content: ByteArray?): Boolean

    /**
     * Delete a file
     *
     * @param fileType the type of file to delete
     * @return true if the file was deleted successfully
     */
    abstract fun deleteFile(fileType: BridgeFileType): Boolean

    /**
     * Check if a file exists
     *
     * @param fileType the type of file to check
     * @return true if the file exists
     */
    abstract fun isFileExists(fileType: BridgeFileType): Boolean

    /**
     * Download content from a URL and save it to a file
     *
     * @param url  the URL to download content from
     * @param path the path to save the content to
     * @return true if the content was downloaded successfully
     */
    abstract fun downloadContent(url: String, path: String): Boolean

    /**
     * Get the content of a logged message from the database
     *
     * @param conversationId the ID of the conversation
     * @return the content of the message
     */
    abstract fun getLoggedMessageIds(conversationId: String, limit: Int): List<Long>

    /**
     * Get the content of a logged message from the database
     *
     * @param id the ID of the message logger message
     * @return the content of the message
     */
    abstract fun getMessageLoggerMessage(conversationId: String, id: Long): ByteArray?

    /**
     * Add a message to the message logger database
     *
     * @param id      the ID of the message logger message
     * @param message the content of the message
     */
    abstract fun addMessageLoggerMessage(conversationId: String, id: Long, message: ByteArray)

    /**
     * Delete a message from the message logger database
     *
     * @param id the ID of the message logger message
     */
    abstract fun deleteMessageLoggerMessage(conversationId: String, id: Long)

    /**
     * Clear the message logger database
     */
    abstract fun clearMessageLogger()

    /**
     * Fetch the translations
     *
     * @return the translations result
     */
    abstract fun fetchTranslations(): LocaleResult

    /**
     * Get check for updates last time
     * @return the last time check for updates was done
     */
    abstract fun getAutoUpdaterTime(): Long

    /**
     * Set check for updates last time
     * @param time the time to set
     */
    abstract fun setAutoUpdaterTime(time: Long)
}