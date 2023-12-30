package me.rhunk.snapenhance.core.manager.impl

import android.content.Intent
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.action.impl.BulkMessagingAction
import me.rhunk.snapenhance.core.action.impl.CleanCache
import me.rhunk.snapenhance.core.action.impl.ExportChatMessages
import me.rhunk.snapenhance.core.action.impl.ExportMemories
import me.rhunk.snapenhance.core.manager.Manager

class ActionManager(
    private val modContext: ModContext,
) : Manager {

    private val actions by lazy {
        mapOf(
            EnumAction.CLEAN_CACHE to CleanCache::class,
            EnumAction.EXPORT_CHAT_MESSAGES to ExportChatMessages::class,
            EnumAction.BULK_MESSAGING_ACTION to BulkMessagingAction::class,
            EnumAction.EXPORT_MEMORIES to ExportMemories::class,
        ).map {
            it.key to it.value.java.getConstructor().newInstance().apply {
                this.context = modContext
            }
        }.toMap().toMutableMap()
    }

    override fun init() {
    }

    fun onNewIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EnumAction.ACTION_PARAMETER) ?: return
        intent.removeExtra(EnumAction.ACTION_PARAMETER)
        execute(EnumAction.entries.find { it.key == action } ?: return)
    }

    fun execute(enumAction: EnumAction) {
        val action = actions[enumAction] ?: return
        action.run()
        if (enumAction.exitOnFinish) {
            modContext.forceCloseApp()
        }
    }
}