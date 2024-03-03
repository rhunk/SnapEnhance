package me.rhunk.snapenhance.core.wrapper.impl.composer

import me.rhunk.snapenhance.core.SnapEnhance
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import java.lang.reflect.Proxy

fun createComposerFunction(block: (args: Array<*>) -> Any?): Any {
    return SnapEnhance.classCache.composerFunctionActionAdapter.constructors.first().newInstance(
        Proxy.newProxyInstance(
            SnapEnhance.classCache.composerAction.classLoader,
            arrayOf(SnapEnhance.classCache.composerAction),
        ) { _, _, args ->
            block(args?.get(0) as Array<*>)
        }
    )
}

class ComposerViewNode(obj: Long) : AbstractWrapper(obj) {
    fun getAttribute(name: String): Any? {
        return SnapEnhance.classCache.nativeBridge.methods.firstOrNull {
            it.name == "getValueForAttribute"
        }?.invoke(null, instanceNonNull(), name)
    }

    fun setAttribute(name: String, value: Any) {
        SnapEnhance.classCache.nativeBridge.methods.firstOrNull {
            it.name == "setValueForAttribute"
        }?.invoke(null, instanceNonNull(), name, value, false)
    }

    fun getChildren(): List<ComposerViewNode> {
        return ((SnapEnhance.classCache.nativeBridge.methods.firstOrNull {
            it.name == "getRetainedViewNodeChildren"
        }?.invoke(null, instanceNonNull(), 1))!! as? LongArray)?.map {
            ComposerViewNode(it)
        } ?: emptyList()
    }

    fun getClassName(): String {
        return SnapEnhance.classCache.nativeBridge.methods.firstOrNull {
            it.name == "getViewClassName"
        }?.invoke(null, instanceNonNull()).toString()
    }

    override fun toString(): String {
        return SnapEnhance.classCache.nativeBridge.methods.firstOrNull {
            it.name == "getViewNodeDebugDescription"
        }?.invoke(null, instanceNonNull()).toString()
    }
}