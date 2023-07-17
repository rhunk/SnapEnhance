package me.rhunk.snapenhance.data

class SnapClassCache (
    private val classLoader: ClassLoader
) {
    val snapUUID by lazy { findClass("com.snapchat.client.messaging.UUID") }
    val composerLocalSubscriptionStore by lazy { findClass("com.snap.plus.lib.common.ComposerLocalSubscriptionStore") }
    val snapManager by lazy { findClass("com.snapchat.client.messaging.SnapManager\$CppProxy") }
    val conversationManager by lazy { findClass("com.snapchat.client.messaging.ConversationManager\$CppProxy") }
    val presenceSession by lazy { findClass("com.snapchat.talkcorev3.PresenceSession\$CppProxy") }
    val message by lazy { findClass("com.snapchat.client.messaging.Message") }
    val messageUpdateEnum by lazy { findClass("com.snapchat.client.messaging.MessageUpdate") }
    val unifiedGrpcService by lazy { findClass("com.snapchat.client.grpc.UnifiedGrpcService\$CppProxy") }
    val networkApi by lazy { findClass("com.snapchat.client.network_api.NetworkApi\$CppProxy") }
    val messageDestinations by lazy { findClass("com.snapchat.client.messaging.MessageDestinations") }
    val localMessageContent by lazy { findClass("com.snapchat.client.messaging.LocalMessageContent") }
    val feedEntry by lazy { findClass("com.snapchat.client.messaging.FeedEntry") }
    val conversation by lazy { findClass("com.snapchat.client.messaging.Conversation") }
    val feedManager by lazy { findClass("com.snapchat.client.messaging.FeedManager\$CppProxy") }
    
    //SPOOF MAPPINGS
    val chromiumDeviceInfo by lazy { findClass("org.chromium.base.BuildInfo")}
    val chromiumStaticDirectory by lazy { findClass("org.chromium.base.PathUtils")}
    //val chromiumSysUtils by lazy { findClass("org.chromium.base.SysUtils")}
    val chromiumJNIUtils by lazy { findClass("org.chromium.base.JNIUtils")}
    

    private fun findClass(className: String): Class<*> {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Failed to find class $className", e)
        }
    }
}