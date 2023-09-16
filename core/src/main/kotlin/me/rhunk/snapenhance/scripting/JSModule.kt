package me.rhunk.snapenhance.scripting

import me.rhunk.snapenhance.core.logger.AbstractLogger
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import org.mozilla.javascript.Context
import org.mozilla.javascript.FunctionObject
import org.mozilla.javascript.ScriptableObject

class JSModule(
    val moduleInfo: ModuleInfo,
    val content: String,
) {
    lateinit var logger: AbstractLogger
    private lateinit var scope: ScriptableObject

    companion object {
        @JvmStatic
        fun logDebug(message: String) {
            println(message)
        }
    }

    fun load() {
        val context = Context.enter()
        context.optimizationLevel = -1
        scope = context.initSafeStandardObjects()
        scope.putConst("module", scope, moduleInfo)

        scope.putConst("logDebug", scope,
            FunctionObject("logDebug", JSModule::class.java.getDeclaredMethod("logDebug", String::class.java), scope)
        )

        context.evaluateString(scope, content, moduleInfo.name, 1, null)
    }

    fun unload() {
        val context = Context.enter()
        context.evaluateString(scope, "if (typeof module.onUnload === 'function') module.onUnload();", "onUnload", 1, null)
        Context.exit()
    }

    fun callOnCoreLoad() {
        val context = Context.enter()
        context.evaluateString(scope, "if (typeof module.onCoreLoad === 'function') module.onCoreLoad();", "onCoreLoad", 1, null)
        Context.exit()
    }

    fun callOnManagerLoad() {
        val context = Context.enter()
        context.evaluateString(scope, "if (typeof module.onManagerLoad === 'function') module.onManagerLoad();", "onManagerLoad", 1, null)
        Context.exit()
    }
}