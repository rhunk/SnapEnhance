package me.rhunk.snapenhance.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

object Hooker {
    private fun newMethodHook(
        stage: HookStage,
        consumer: (HookAdapter) -> Unit,
        filter: ((HookAdapter) -> Boolean) = { true }
    ) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam<*>) {
            if (stage != HookStage.BEFORE) return
            with(HookAdapter(param)) {
                if (!filter(this)) return
                consumer(this)
            }
        }

        override fun afterHookedMethod(param: MethodHookParam<*>) {
            if (stage != HookStage.AFTER) return
            with(HookAdapter(param)) {
                if (!filter(this)) return
                consumer(this)
            }
        }
    }

    fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): Set<XC_MethodHook.Unhook> = XposedBridge.hookAllMethods(clazz, methodName, newMethodHook(stage, consumer))

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
        return XposedBridge.hookMethod(member, newMethodHook(stage, consumer))
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