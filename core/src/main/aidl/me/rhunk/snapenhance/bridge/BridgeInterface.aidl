package me.rhunk.snapenhance.bridge;

import java.util.List;
import me.rhunk.snapenhance.bridge.DownloadCallback;

interface BridgeInterface {
        /**
         * Create a file if it doesn't exist, and read it
         *
         * @param fileType       the type of file to create and read
         * @param defaultContent the default content to write to the file if it doesn't exist
         * @return the content of the file
         */
        byte[] createAndReadFile(int fileType, in byte[] defaultContent);

        /**
         * Read a file
         *
         * @param fileType the type of file to read
         * @return the content of the file
         */
        byte[] readFile(int fileType);

        /**
         * Write a file
         *
         * @param fileType the type of file to write
         * @param content  the content to write to the file
         * @return true if the file was written successfully
         */
        boolean writeFile(int fileType, in byte[] content);

        /**
         * Delete a file
         *
         * @param fileType the type of file to delete
         * @return true if the file was deleted successfully
         */
        boolean deleteFile(int fileType);

        /**
         * Check if a file exists
         *
         * @param fileType the type of file to check
         * @return true if the file exists
         */
        boolean isFileExists(int fileType);

        /**
         * Get the content of a logged message from the database
         *
         * @param conversationId the ID of the conversation
         * @return the content of the message
         */
        long[] getLoggedMessageIds(String conversationId, int limit);

        /**
         * Get the content of a logged message from the database
         *
         * @param id the ID of the message logger message
         * @return the content of the message
         */
        @nullable byte[] getMessageLoggerMessage(String conversationId, long id);

        /**
         * Add a message to the message logger database
         *
         * @param id      the ID of the message logger message
         * @param message the content of the message
         */
        void addMessageLoggerMessage(String conversationId, long id, in byte[] message);

        /**
         * Delete a message from the message logger database
         *
         * @param id the ID of the message logger message
         */
        void deleteMessageLoggerMessage(String conversationId, long id);

        /**
         * Clear the message logger database
         */
        void clearMessageLogger();

        /**
         * Fetch the translations
         *
         * @return the translations result
         */
        Map<String, String> fetchTranslations();

        /**
         * Get check for updates last time
         * @return the last time check for updates was done
         */
        long getAutoUpdaterTime();

        /**
         * Set check for updates last time
         * @param time the time to set
         */
        void setAutoUpdaterTime(long time);

        /**
         * Enqueue a download
         */
        void enqueueDownload(in Intent intent, DownloadCallback callback);
}