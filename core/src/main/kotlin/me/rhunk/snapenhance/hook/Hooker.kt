package me.rhunk.snapenhance.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

typealias HookFilter = (HookAdapter) -> Boolean
typealias HookConsumer = (HookAdapter) -> Unit

object Hooker {
    inline fun newMethodHook(
        stage: HookStage,
        crossinline consumer: HookConsumer,
        crossinline filter: HookFilter = { true }
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
        crossinline consumer: HookConsumer
    ): Set<XC_MethodHook.Unhook> = hook(clazz, methodName, stage, { true }, consumer)

    inline fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        crossinline filter: HookFilter,
        crossinline consumer: HookConsumer
    ): Set<XC_MethodHook.Unhook> = XposedBridge.hookAllMethods(clazz, methodName, newMethodHook(stage, consumer, filter))

    inline fun hook(
        member: Member,
        stage: HookStage,
        crossinline consumer: HookConsumer
    ): XC_MethodHook.Unhook {
        return hook(member, stage, { true }, consumer)
    }

    inline fun hook(
        member: Member,
        stage: HookStage,
        crossinline filter: HookFilter,
        crossinline consumer: HookConsumer
    ): XC_MethodHook.Unhook {
        return XposedBridge.hookMethod(member, newMethodHook(stage, consumer, filter))
    }


    inline fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        crossinline consumer: HookConsumer
    ) {
        XposedBridge.hookAllConstructors(clazz, newMethodHook(stage, consumer))
    }

    inline fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        crossinline filter: HookFilter,
        crossinline consumer: HookConsumer
    ) {
        XposedBridge.hookAllConstructors(clazz, newMethodHook(stage, consumer, filter))
    }

    inline fun hookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: HookConsumer
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
        crossinline hookConsumer: HookConsumer
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
        crossinline hookConsumer: HookConsumer
    ) {
        val unhooks: MutableSet<XC_MethodHook.Unhook> = HashSet()
        hook(clazz, methodName, stage) { param->
            if (param.nullableThisObject<Any>() != instance) return@hook
            hookConsumer(param)
            unhooks.forEach{ it.unhook() }
        }.also { unhooks.addAll(it) }
    }
}

inline fun Class<*>.hookConstructor(
    stage: HookStage,
    crossinline consumer: HookConsumer
) = Hooker.hookConstructor(this, stage, consumer)

inline fun Class<*>.hookConstructor(
    stage: HookStage,
    crossinline filter: HookFilter,
    crossinline consumer: HookConsumer
) = Hooker.hookConstructor(this, stage, filter, consumer)

inline fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    crossinline consumer: HookConsumer
): Set<XC_MethodHook.Unhook> = Hooker.hook(this, methodName, stage, consumer)

inline fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    crossinline filter: HookFilter,
    crossinline consumer: HookConsumer
): Set<XC_MethodHook.Unhook> = Hooker.hook(this, methodName, stage, filter, consumer)

inline fun Member.hook(
    stage: HookStage,
    crossinline consumer: HookConsumer
): XC_MethodHook.Unhook = Hooker.hook(this, stage, consumer)

inline fun Member.hook(
    stage: HookStage,
    crossinline filter: HookFilter,
    crossinline consumer: HookConsumer
): XC_MethodHook.Unhook = Hooker.hook(this, stage, filter, consumer)