package me.rhunk.snapenhance.bridge;

interface MessageLoggerInterface {
    /**
     * Get the ids of the messages that are logged
     * @return message ids that are logged
     */
    long[] getLoggedIds(in String[] conversationIds, int limit);

    /**
     * Get the content of a logged message from the database
     */
    @nullable byte[] getMessage(String conversationId, long id);

    /**
     * Add a message to the message logger database if it is not already there
     */
    boolean addMessage(String conversationId, long id, in byte[] message);

    /**
     * Delete a message from the message logger database
     */
    void deleteMessage(String conversationId, long id);
}