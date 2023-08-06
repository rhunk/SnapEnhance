package me.rhunk.snapenhance.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member
import java.lang.reflect.Method

object Hooker {
    inline fun newMethodHook(
        stage: HookStage,
        crossinline consumer: (HookAdapter) -> Unit,
        crossinline filter: ((HookAdapter) -> Boolean) = { true }
    ): XC_MethodHook {
        return if (stage == HookStage.BEFORE) object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                HookAdapter(param).takeIf(filter)?.also(consumer)
            }
        } else object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                HookAdapter(param).takeIf(filter)?.also(consumer)
            }
        }
    }

    inline fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        crossinline filter: (HookAdapter) -> Boolean,
        noinline consumer: (HookAdapter) -> Unit
    ): Set<XC_MethodHook.Unhook> = XposedBridge.hookAllMethods(clazz, methodName, newMethodHook(stage, consumer, filter))

    inline fun hook(
        member: Member,
        stage: HookStage,
        crossinline filter: ((HookAdapter) -> Boolean),
        crossinline consumer: (HookAdapter) -> Unit
    ): XC_MethodHook.Unhook {
        return XposedBridge.hookMethod(member, newMethodHook(stage, consumer, filter))
    }

    fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): Set<XC_MethodHook.Unhook> = hook(clazz, methodName, stage, { true }, consumer)

    fun hook(
        member: Member,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): XC_MethodHook.Unhook {
        return hook(member, stage, { true }, consumer)
    }

    fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ) {
        XposedBridge.hookAllConstructors(clazz, newMethodHook(stage, consumer))
    }

    fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        filter: ((HookAdapter) -> Boolean),
        consumer: (HookAdapter) -> Unit
    ) {
        XposedBridge.hookAllConstructors(clazz, newMethodHook(stage, consumer, filter))
    }

    inline fun hookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<XC_MethodHook.Unhook> = HashSet()
        hook(clazz, methodName, stage) { param->
            if (param.nullableThisObject<Any>().let {
                if (it == null) unhooks.forEach { u -> u.unhook() }
                it != instance
            }) return@hook
            hookConsumer(param)
        }.also { unhooks.addAll(it) }
    }

    inline fun ephemeralHook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<XC_MethodHook.Unhook> = HashSet()
        hook(clazz, methodName, stage) { param->
            hookConsumer(param)
            unhooks.forEach{ it.unhook() }
        }.also { unhooks.addAll(it) }
    }

    inline fun ephemeralHookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<XC_MethodHook.Unhook> = HashSet()
        hook(clazz, methodName, stage) { param->
            if (param.nullableThisObject<Any>() != instance) return@hook
            hookConsumer(param)
            unhooks.forEach{ it.unhook() }
        }.also { unhooks.addAll(it) }
    }
}

fun Class<*>.hookConstructor(
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
) = Hooker.hookConstructor(this, stage, consumer)

fun Class<*>.hookConstructor(
    stage: HookStage,
    filter: ((HookAdapter) -> Boolean),
    consumer: (HookAdapter) -> Unit
) = Hooker.hookConstructor(this, stage, filter, consumer)

fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
): Set<XC_MethodHook.Unhook> = Hooker.hook(this, methodName, stage, consumer)

fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    filter: (HookAdapter) -> Boolean,
    consumer: (HookAdapter) -> Unit
): Set<XC_MethodHook.Unhook> = Hooker.hook(this, methodName, stage, filter, consumer)

fun Member.hook(
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
): XC_MethodHook.Unhook = Hooker.hook(this, stage, consumer)

fun Member.hook(
    stage: HookStage,
    filter: ((HookAdapter) -> Boolean),
    consumer: (HookAdapter) -> Unit
): XC_MethodHook.Unhook = Hooker.hook(this, stage, filter, consumer)

fun Array<Method>.hookAll(stage: HookStage, param: (HookAdapter) -> Unit) {
    filter { it.declaringClass != Object::class.java }.forEach {
        it.hook(stage, param)
    }
}
