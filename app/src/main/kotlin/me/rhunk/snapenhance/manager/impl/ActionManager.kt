package me.rhunk.snapenhance.manager.impl

import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.action.impl.CheckForUpdates
import me.rhunk.snapenhance.action.impl.CleanCache
import me.rhunk.snapenhance.action.impl.ClearMessageLogger
import me.rhunk.snapenhance.action.impl.OpenMap
import me.rhunk.snapenhance.action.impl.RefreshMappings
import me.rhunk.snapenhance.manager.Manager
import kotlin.reflect.KClass

class ActionManager(
    private val context: ModContext,
) : Manager {
    private val actions = mutableMapOf<String, AbstractAction>()
    fun getActions() = actions.values.toList()
    private fun load(clazz: KClass<out AbstractAction>) {
        val action = clazz.java.newInstance()
        action.context = context
        actions[action.nameKey] = action
    }
    override fun init() {
        load(CleanCache::class)
        load(ClearMessageLogger::class)
        load(RefreshMappings::class)
        load(OpenMap::class)
        load(CheckForUpdates::class)

        actions.values.forEach(AbstractAction::init)
    }
}