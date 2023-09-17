package me.rhunk.snapenhance.scripting

import me.rhunk.snapenhance.core.logger.AbstractLogger
import me.rhunk.snapenhance.scripting.ktx.contextScope
import me.rhunk.snapenhance.scripting.ktx.putFunction
import me.rhunk.snapenhance.scripting.ktx.scriptableObject
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class JSModule(
    val moduleInfo: ModuleInfo,
    val content: String,
) {
    lateinit var logger: AbstractLogger
    private lateinit var moduleObject: ScriptableObject

    fun load(block: ScriptableObject.() -> Unit) {
        contextScope {
            moduleObject = initSafeStandardObjects()
            moduleObject.putConst("module", moduleObject, scriptableObject {
                putConst("info", this, scriptableObject {
                    putConst("name", this, moduleInfo.name)
                    putConst("version", this, moduleInfo.version)
                    putConst("description", this, moduleInfo.description)
                    putConst("author", this, moduleInfo.author)
                    putConst("minSnapchatVersion", this, moduleInfo.minSnapchatVersion)
                    putConst("minSEVersion", this, moduleInfo.minSEVersion)
                    putConst("grantPermissions", this, moduleInfo.grantPermissions)
                })
            })

            moduleObject.putFunction("setField") { args ->
                val obj = args?.get(0) as? NativeJavaObject ?: return@putFunction Undefined.instance
                val name = args[1].toString()
                val value = args[2]
                val field = obj.unwrap().javaClass.declaredFields.find { it.name == name } ?: return@putFunction Undefined.instance
                field.isAccessible = true
                field.set(obj.unwrap(), value)
                Undefined.instance
            }

            moduleObject.putFunction("getField") { args ->
                val obj = args?.get(0) as? NativeJavaObject ?: return@putFunction Undefined.instance
                val name = args[1].toString()
                val field = obj.unwrap().javaClass.declaredFields.find { it.name == name } ?: return@putFunction Undefined.instance
                field.isAccessible = true
                field.get(obj.unwrap())
            }

            moduleObject.putFunction("logInfo") { args ->
                logger.info(args?.joinToString(" ") ?: "")
                Undefined.instance
            }

            block(moduleObject)
            evaluateString(moduleObject, content, moduleInfo.name, 1, null)
        }
    }

    fun unload() {
        callFunction("module.onUnload")
    }

    fun callFunction(name: String, vararg args: Any?) {
        contextScope {
            name.split(".").also { split ->
                val function = split.dropLast(1).fold(moduleObject) { obj, key ->
                    obj.get(key, obj) as? ScriptableObject ?: return@contextScope
                }.get(split.last(), moduleObject) as? Function ?: return@contextScope

                function.call(this, moduleObject, moduleObject, args)
            }
        }
    }
}