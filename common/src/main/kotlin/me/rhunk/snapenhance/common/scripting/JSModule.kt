package me.rhunk.snapenhance.common.scripting

import android.os.Handler
import android.widget.Toast
import me.rhunk.snapenhance.common.scripting.ktx.contextScope
import me.rhunk.snapenhance.common.scripting.ktx.putFunction
import me.rhunk.snapenhance.common.scripting.ktx.scriptableObject
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Modifier

class JSModule(
    val scriptRuntime: ScriptRuntime,
    val moduleInfo: ModuleInfo,
    val content: String,
) {
    val extras = mutableMapOf<String, Any>()
    private lateinit var moduleObject: ScriptableObject

    fun load(block: ScriptableObject.() -> Unit) {
        contextScope {
            val classLoader = scriptRuntime.androidContext.classLoader
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
                val value = args[2].let {
                    when (it) {
                        is Wrapper -> it.unwrap()
                        else -> it
                    }
                }
                val field = obj.unwrap().javaClass.declaredFields.find { it.name == name } ?: return@putFunction Undefined.instance
                field.isAccessible = true
                field.set(obj.unwrap(), value.toPrimitiveValue(lazy { field.type.name }))
                Undefined.instance
            }

            moduleObject.putFunction("getField") { args ->
                val obj = args?.get(0) as? NativeJavaObject ?: return@putFunction Undefined.instance
                val name = args[1].toString()
                val field = obj.unwrap().javaClass.declaredFields.find { it.name == name } ?: return@putFunction Undefined.instance
                field.isAccessible = true
                field.get(obj.unwrap())
            }

            moduleObject.putFunction("findClass") {
                val className = it?.get(0).toString()
                classLoader.loadClass(className)
            }

            moduleObject.putFunction("type") { args ->
                val className = args?.get(0).toString()
                val clazz = classLoader.loadClass(className)

                scriptableObject("JavaClassWrapper") {
                    putFunction("newInstance") newInstance@{ args ->
                        val constructor = clazz.declaredConstructors.find {
                            it.parameterCount == (args?.size ?: 0)
                        } ?: return@newInstance Undefined.instance
                        constructor.newInstance(*args ?: emptyArray())
                    }

                    clazz.declaredMethods.filter { Modifier.isStatic(it.modifiers) }.forEach { method ->
                        putFunction(method.name) { args ->
                            clazz.declaredMethods.find {
                                it.name == method.name && it.parameterTypes.zip(args ?: emptyArray()).all { (type, arg) ->
                                    type.isAssignableFrom(arg?.javaClass ?: return@all false)
                                }
                            }?.invoke(null, *args ?: emptyArray())
                        }
                    }

                    clazz.declaredFields.filter { Modifier.isStatic(it.modifiers) }.forEach { field ->
                        field.isAccessible = true
                        defineProperty(field.name, { field.get(null)}, { value -> field.set(null, value) }, 0)
                    }
                }
            }

            moduleObject.putFunction("logInfo") { args ->
                scriptRuntime.logger.info(args?.joinToString(" ") {
                    when (it) {
                        is Wrapper -> it.unwrap().toString()
                        else -> it.toString()
                    }
                } ?: "null")
                Undefined.instance
            }

            for (toastFunc in listOf("longToast", "shortToast")) {
                moduleObject.putFunction(toastFunc) { args ->
                    Handler(scriptRuntime.androidContext.mainLooper).post {
                        Toast.makeText(
                            scriptRuntime.androidContext,
                            args?.joinToString(" ") ?: "",
                            if (toastFunc == "longToast") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        ).show()
                    }
                    Undefined.instance
                }
            }
            block(moduleObject)
            extras.forEach { (key, value) ->
                moduleObject.putConst(key, moduleObject, value)
            }
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

                runCatching {
                    function.call(this, moduleObject, moduleObject, args)
                }.onFailure {
                    scriptRuntime.logger.error("Error while calling function $name", it)
                }
            }
        }
    }
}