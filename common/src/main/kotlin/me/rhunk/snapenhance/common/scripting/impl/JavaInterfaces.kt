package me.rhunk.snapenhance.common.scripting.impl

import me.rhunk.snapenhance.common.scripting.bindings.AbstractBinding
import me.rhunk.snapenhance.common.scripting.bindings.BindingSide
import me.rhunk.snapenhance.common.scripting.ktx.contextScope
import me.rhunk.snapenhance.common.scripting.ktx.putFunction
import me.rhunk.snapenhance.common.scripting.ktx.scriptableObject
import java.lang.reflect.Proxy

class JavaInterfaces : AbstractBinding("java-interfaces", BindingSide.COMMON) {
    override fun getObject() = scriptableObject {
        putFunction("runnable") {
            val function = it?.get(0) as? org.mozilla.javascript.Function ?: return@putFunction null
            Runnable {
                contextScope {
                    function.call(
                        this,
                        this@scriptableObject,
                        this@scriptableObject,
                        emptyArray()
                    )
                }
            }
        }

        putFunction("newProxy") { arguments ->
            val javaInterface = arguments?.get(0) as? Class<*> ?: return@putFunction null
            val function = arguments[1] as? org.mozilla.javascript.Function ?: return@putFunction null

            Proxy.newProxyInstance(
                javaInterface.classLoader,
                arrayOf(javaInterface)
            ) { instance, method, args ->
                contextScope {
                    function.call(
                        this,
                        this@scriptableObject,
                        this@scriptableObject,
                        arrayOf(instance, method.name, (args ?: emptyArray<Any>()).toList())
                    )
                }
            }
        }
    }
}