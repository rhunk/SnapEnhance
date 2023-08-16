package me.rhunk.snapenhance.bridge;

import java.util.List;
import me.rhunk.snapenhance.bridge.DownloadCallback;

interface BridgeInterface {
        /**
        * Execute a file operation
        */
        byte[] fileOperation(int action, int fileType, in @nullable byte[] content);

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
         * Fetch the locales
         *
         * @return the locale result
         */
        Map<String, String> fetchLocales(String userLocale);

        /**
         * Enqueue a download
         */
        void enqueueDownload(in Intent intent, DownloadCallback callback);
}