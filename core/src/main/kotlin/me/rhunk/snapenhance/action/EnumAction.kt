package me.rhunk.snapenhance.action

import me.rhunk.snapenhance.action.impl.CleanCache
import me.rhunk.snapenhance.action.impl.ExportChatMessages
import me.rhunk.snapenhance.action.impl.OpenMap
import kotlin.reflect.KClass

enum class EnumAction(
    val key: String,
    val clazz: KClass<out AbstractAction>,
    val exitOnFinish: Boolean = false,
    val isCritical: Boolean = false,
) {
    CLEAN_CACHE("clean_snapchat_cache", CleanCache::class, exitOnFinish = true),
    EXPORT_CHAT_MESSAGES("export_chat_messages", ExportChatMessages::class),
    OPEN_MAP("open_map", OpenMap::class);
}