package me.rhunk.snapenhance.util

import de.robv.android.xposed.XC_MethodHook
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class CallbackBuilder(
    private val callbackClass: Class<*>
) {
    internal class Override(
        val methodName: String,
        val shouldUnhook: Boolean = true,
        val callback: (HookAdapter) -> Unit
    )

    private val methodOverrides = mutableListOf<Override>()

    fun override(methodName: String, shouldUnhook: Boolean = true, callback: (HookAdapter) -> Unit = {}): CallbackBuilder {
        methodOverrides.add(Override(methodName, shouldUnhook, callback))
        return this
    }

    fun build(): Any {
        //get the first param of the first constructor to get the class of the invoker
        val invokerClass: Class<*> = callbackClass.constructors[0].parameterTypes[0]
        //get the invoker field based on the invoker class
        val invokerField = callbackClass.fields.first { field: Field ->
            field.type.isAssignableFrom(invokerClass)
        }
        //get the callback field based on the callback class
        val callbackInstance = createEmptyObject(callbackClass.constructors[0])!!
        val callbackInstanceHashCode: Int = callbackInstance.hashCode()
        val callbackInstanceClass = callbackInstance.javaClass

        val unhooks = mutableListOf<XC_MethodHook.Unhook>()

        callbackInstanceClass.methods.forEach { method ->
            if (method.declaringClass != callbackInstanceClass) return@forEach
            if (Modifier.isPrivate(method.modifiers)) return@forEach

            //default hook that unhooks the callback and returns null
            val defaultHook: (HookAdapter) -> Boolean = defaultHook@{
                //checking invokerField ensure that's the callback was created by the CallbackBuilder
                if (invokerField.get(it.thisObject()) != null) return@defaultHook false
                if ((it.thisObject() as Any).hashCode() != callbackInstanceHashCode) return@defaultHook false
                it.setResult(null)
                true
            }

            var hook: (HookAdapter) -> Unit = { defaultHook(it) }

            //override the default hook if the method is in the override list
            methodOverrides.find { it.methodName == method.name }?.run {
                hook = {
                    if (defaultHook(it)) {
                        callback(it)
                        if (shouldUnhook) unhooks.forEach { unhook -> unhook.unhook() }
                    }
                }
            }

            unhooks.add(Hooker.hook(method, HookStage.BEFORE, hook))
        }
        return callbackInstance
    }

    companion object {
        fun createEmptyObject(constructor: Constructor<*>): Any? {
            //compute the args for the constructor with null or default primitive values
            val args = constructor.parameterTypes.map { type: Class<*> ->
                if (type.isPrimitive) {
                    when (type.name) {
                        "boolean" -> return@map false
                        "byte" -> return@map 0.toByte()
                        "char" -> return@map 0.toChar()
                        "short" -> return@map 0.toShort()
                        "int" -> return@map 0
                        "long" -> return@map 0L
                        "float" -> return@map 0f
                        "double" -> return@map 0.0
                    }
                }
                null
            }.toTypedArray()
            return constructor.newInstance(*args)
        }

    }
}