package me.rhunk.snapenhance.manager.impl

import android.content.Intent
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.action.EnumAction
import me.rhunk.snapenhance.manager.Manager

class ActionManager(
    private val modContext: ModContext,
) : Manager {
    companion object {
        const val ACTION_PARAMETER = "se_action"
    }
    private val actions = mutableMapOf<String, AbstractAction>()

    override fun init() {
        EnumAction.entries.forEach { enumAction ->
            actions[enumAction.key] = enumAction.clazz.java.getConstructor().newInstance().apply {
                this.context = modContext
            }
        }
    }

    fun onNewIntent(intent: Intent?) {
        val action = intent?.getStringExtra(ACTION_PARAMETER) ?: return
        execute(EnumAction.entries.find { it.key == action } ?: return)
        intent.removeExtra(ACTION_PARAMETER)
    }

    fun execute(action: EnumAction) {
        actions[action.key]?.run()
        if (action.exitOnFinish) {
            modContext.forceCloseApp()
        }
    }
}