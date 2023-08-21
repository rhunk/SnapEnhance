package me.rhunk.snapenhance.bridge;

interface SyncCallback {
    /**
    * Called when the friend data has been synced
    * @param uuid The uuid of the friend to sync
    * @return The serialized friend data
    */
    @nullable String syncFriend(String uuid);

    /**
    * Called when the conversation data has been synced
    * @param uuid The uuid of the conversation to sync
    * @return The serialized conversation data
    */
    @nullable String syncGroup(String uuid);
}