package me.rhunk.snapenhance

object Constants {
    const val TAG = "SnapEnhance"
    const val SNAPCHAT_PACKAGE_NAME = "com.snapchat.android"

    const val VIEW_INJECTED_CODE = 0x7FFFFF02

    val ARROYO_MEDIA_CONTAINER_PROTO_PATH = intArrayOf(4, 4)
    val ARROYO_STRING_CHAT_MESSAGE_PROTO = ARROYO_MEDIA_CONTAINER_PROTO_PATH + intArrayOf(2, 1)
    val ARROYO_URL_KEY_PROTO_PATH = intArrayOf(4, 5, 1, 3)

    const val ENCRYPTION_PROTO_INDEX = 19
    const val ENCRYPTION_PROTO_INDEX_V2 = 4

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
}