package me.rhunk.snapenhance.core.util.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member
import java.util.function.Consumer

@Suppress("UNCHECKED_CAST")
class HookAdapter(
    private val methodHookParam: XC_MethodHook.MethodHookParam<*>
) {
    fun <T : Any> thisObject(): T {
        return methodHookParam.thisObject as T
    }

    fun <T : Any> nullableThisObject(): T? {
        return methodHookParam.thisObject as T?
    }

    fun method(): Member {
        return methodHookParam.method
    }

    fun <T : Any> arg(index: Int): T {
        return methodHookParam.args[index] as T
    }

    fun <T : Any> argNullable(index: Int): T? {
        return methodHookParam.args.getOrNull(index) as T?
    }

    fun setArg(index: Int, value: Any?) {
        if (index < 0 || index >= methodHookParam.args.size) return
        methodHookParam.args[index] = value
    }

    fun args(): Array<Any?> {
        return methodHookParam.args
    }

    fun getResult(): Any? {
        return methodHookParam.result
    }

    fun setResult(result: Any?) {
        methodHookParam.result = result
    }

    fun setThrowable(throwable: Throwable) {
        methodHookParam.throwable = throwable
    }

    fun throwable(): Throwable? {
        return methodHookParam.throwable
    }

    fun invokeOriginal(): Any? {
        return XposedBridge.invokeOriginalMethod(method(), thisObject(), args())
    }

    fun invokeOriginal(args: Array<Any?>): Any? {
        return XposedBridge.invokeOriginalMethod(method(), thisObject(), args)
    }

    fun invokeOriginalSafe(errorCallback: Consumer<Throwable>) {
        invokeOriginalSafe(args(), errorCallback)
    }

    fun invokeOriginalSafe(args: Array<Any?>, errorCallback: Consumer<Throwable>) {
        runCatching {
            setResult(XposedBridge.invokeOriginalMethod(method(), thisObject(), args))
        }.onFailure {
            errorCallback.accept(it)
        }
    }
}