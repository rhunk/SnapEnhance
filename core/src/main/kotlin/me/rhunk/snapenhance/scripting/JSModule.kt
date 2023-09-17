package me.rhunk.snapenhance.scripting

import android.app.Activity
import me.rhunk.snapenhance.core.logger.AbstractLogger
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class JSModule(
    val moduleInfo: ModuleInfo,
    val content: String,
) {
    lateinit var logger: AbstractLogger
    private lateinit var moduleObject: ScriptableObject

    fun load(block: Context.(ScriptableObject) -> Unit) {
        contextScope {
            moduleObject = initSafeStandardObjects()
            moduleObject.putConst("module", moduleObject, buildScriptableObject {
                putConst("info", this, buildScriptableObject {
                    putConst("name", this, moduleInfo.name)
                    putConst("version", this, moduleInfo.version)
                    putConst("description", this, moduleInfo.description)
                    putConst("author", this, moduleInfo.author)
                    putConst("minSnapchatVersion", this, moduleInfo.minSnapchatVersion)
                    putConst("minSEVersion", this, moduleInfo.minSEVersion)
                    putConst("grantPermissions", this, moduleInfo.grantPermissions)
                })
            })

            moduleObject.putFunction("logInfo") { args ->
                logger.info(args?.getOrNull(0)?.toString() ?: "null")
                Undefined.instance
            }

            block(this, moduleObject)
            evaluateString(moduleObject, content, moduleInfo.name, 1, null)
        }
    }

    fun unload() {
        contextScope {
            moduleObject.scriptable("module")?.function("onUnload")?.call(
                this,
                moduleObject,
                moduleObject,
                null
            )
        }
    }

    fun callOnSnapActivity(activity: Activity) {
        contextScope {
            moduleObject.scriptable("module")?.function("onSnapActivity")?.call(
                this,
                moduleObject,
                moduleObject,
                arrayOf(activity)
            )
        }
    }

    fun callOnManagerLoad(context: android.content.Context) {
        contextScope {
            moduleObject.scriptable("module")?.function("onManagerLoad")?.call(
                this,
                moduleObject,
                moduleObject,
                arrayOf(context)
            )
        }
    }
}