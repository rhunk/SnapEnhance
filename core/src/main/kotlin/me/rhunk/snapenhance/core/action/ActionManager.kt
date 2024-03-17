package me.rhunk.snapenhance.core.action

import android.content.Intent
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.action.impl.BulkMessagingAction
import me.rhunk.snapenhance.core.action.impl.CleanCache
import me.rhunk.snapenhance.core.action.impl.ExportChatMessages
import me.rhunk.snapenhance.core.action.impl.ExportMemories
import me.rhunk.snapenhance.core.action.impl.ManageFriendList

class ActionManager(
    private val modContext: ModContext,
) {

    private val actions by lazy {
        mapOf(
            EnumAction.CLEAN_CACHE to CleanCache(),
            EnumAction.EXPORT_CHAT_MESSAGES to ExportChatMessages(),
            EnumAction.BULK_MESSAGING_ACTION to BulkMessagingAction(),
            EnumAction.MANAGE_FRIEND_LIST to ManageFriendList(),
            EnumAction.EXPORT_MEMORIES to ExportMemories(),
        ).map {
            it.key to it.value.apply {
                this.context = modContext
            }
        }.toMap().toMutableMap()
    }

    fun onNewIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EnumAction.ACTION_PARAMETER) ?: return
        intent.removeExtra(EnumAction.ACTION_PARAMETER)
        execute(EnumAction.entries.find { it.key == action } ?: return)
    }

    fun onActivityCreate() {
        actions.values.forEach { it.onActivityCreate() }
    }

    fun execute(enumAction: EnumAction) {
        val action = actions[enumAction] ?: return
        action.run()
        if (enumAction.exitOnFinish) {
            modContext.forceCloseApp()
        }
    }
}