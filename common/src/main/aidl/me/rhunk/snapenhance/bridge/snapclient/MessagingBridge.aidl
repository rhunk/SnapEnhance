package me.rhunk.snapenhance.bridge.snapclient;

import java.util.List;
import me.rhunk.snapenhance.bridge.snapclient.types.Message;
import me.rhunk.snapenhance.bridge.snapclient.SessionStartListener;

interface MessagingBridge {
    boolean isSessionStarted();

    void registerSessionStartListener(in SessionStartListener listener);

    String getMyUserId();

    @nullable Message fetchMessage(String conversationId, String clientMessageId);

    @nullable Message fetchMessageByServerId(String conversationId, String serverMessageId);

    @nullable List<Message> fetchConversationWithMessagesPaginated(String conversationId, int limit, long beforeMessageId);

    @nullable String updateMessage(String conversationId, long clientMessageId, String messageUpdate);

    @nullable String getOneToOneConversationId(String userId);
}