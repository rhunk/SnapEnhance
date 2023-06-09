package me.rhunk.snapenhance.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

object Hooker {
    private fun newMethodHook(
        stage: HookStage,
        consumer: (HookAdapter) -> Unit,
        filter: ((HookAdapter) -> Boolean) = { true }
    ): XC_MethodHook {
        val callEvent = { param: XC_MethodHook.MethodHookParam<*> ->
            HookAdapter(param).takeIf(filter)?.also(consumer)
        }

        return if (stage == HookStage.BEFORE) object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                callEvent(param)
            }
        } else object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                callEvent(param)
            }
        }
    }

    fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): Set<XC_MethodHook.Unhook> = hook(clazz, methodName, stage, { true }, consumer)

    fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        filter: (HookAdapter) -> Boolean,
        consumer: (HookAdapter) -> Unit
    ): Set<XC_MethodHook.Unhook> = XposedBridge.hookAllMethods(clazz, methodName, newMethodHook(stage, consumer, filter))

    fun hook(
        member: Member,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): XC_MethodHook.Unhook {
        return hook(member, stage, { true }, consumer)
    }

    fun hook(
        member: Member,
        stage: HookStage,
        filter: ((HookAdapter) -> Boolean),
        consumer: (HookAdapter) -> Unit
    ): XC_MethodHook.Unhook {
        return XposedBridge.hookMethod(member, newMethodHook(stage, consumer, filter))
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

    fun ephemeralHookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<XC_MethodHook.Unhook> = HashSet()
        hook(clazz, methodName, stage) { param->
            if (param.thisObject<Any>() != instance) return@hook
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