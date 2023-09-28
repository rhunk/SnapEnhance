package me.rhunk.snapenhance.bridge;

import java.util.List;
import me.rhunk.snapenhance.bridge.DownloadCallback;
import me.rhunk.snapenhance.bridge.SyncCallback;
import me.rhunk.snapenhance.bridge.scripting.IScripting;
import me.rhunk.snapenhance.bridge.e2ee.E2eeInterface;

interface BridgeInterface {
    /**
    * broadcast a log message
    */
    void broadcastLog(String tag, String level, String message);

    /**
    * Execute a file operation
    * @param fileType the corresponding file type (see BridgeFileType)
    */
    byte[] fileOperation(int action, int fileType, in @nullable byte[] content);

    /**
     * Get the content of a logged message from the database
     * @return message ids that are logged
     */
    long[] getLoggedMessageIds(String conversationId, int limit);

    /**
     * Get the content of a logged message from the database
     */
    @nullable byte[] getMessageLoggerMessage(String conversationId, long id);

    /**
     * Add a message to the message logger database
     */
    void addMessageLoggerMessage(String conversationId, long id, in byte[] message);

    /**
     * Delete a message from the message logger database
     */
    void deleteMessageLoggerMessage(String conversationId, long id);

    /**
    * Get the application APK path (assets for the conversation exporter)
    */
    String getApplicationApkPath();

    /**
     * Fetch the locales
     *
     * @return the map of locales (key: locale short name, value: locale data as json)
     */
    Map<String, String> fetchLocales(String userLocale);

    /**
     * Enqueue a download
     */
    void enqueueDownload(in Intent intent, DownloadCallback callback);

    /**
    * Get rules for a given user or conversation
    * @return list of rules (MessagingRuleType)
    */
    List<String> getRules(String uuid);

    /**
    * Get all ids for a specific rule
    * @param type rule type (MessagingRuleType)
    * @return list of ids
    */
    List<String> getRuleIds(String type);

    /**
    * Update rule for a giver user or conversation
    *
    * @param type rule type (MessagingRuleType)
    */
    void setRule(String uuid, String type, boolean state);

    /**
    * Sync groups and friends
    */
    oneway void sync(SyncCallback callback);

    /**
    * Trigger sync for an id
    */
    void triggerSync(String scope, String id);

    /**
    * Pass all groups and friends to be able to add them to the database
    * @param groups list of groups (MessagingGroupInfo as json string)
    * @param friends list of friends (MessagingFriendInfo as json string)
    */
    oneway void passGroupsAndFriends(in List<String> groups, in List<String> friends);

    IScripting getScriptingInterface();

    E2eeInterface getE2eeInterface();

    void openSettingsOverlay();

    void closeSettingsOverlay();
}