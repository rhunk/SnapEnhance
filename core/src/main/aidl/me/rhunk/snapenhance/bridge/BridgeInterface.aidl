package me.rhunk.snapenhance.bridge;

import java.util.List;
import me.rhunk.snapenhance.bridge.DownloadCallback;
import me.rhunk.snapenhance.bridge.SyncCallback;

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

    String getApplicationApkPath();

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

    /**
    * Get rules for a given user or conversation
    */
    List<String> getRules(String uuid);

    /**
    * Update rule for a giver user or conversation
    */
    void setRule(String uuid, String type, boolean state);

    /**
    * Sync groups and friends
    */
    oneway void sync(SyncCallback callback);

    /**
    * Pass all groups and friends to be able to add them to the database
    * @param groups serialized groups
    * @param friends serialized friends
    */
    oneway void passGroupsAndFriends(in List<String> groups, in List<String> friends);
}