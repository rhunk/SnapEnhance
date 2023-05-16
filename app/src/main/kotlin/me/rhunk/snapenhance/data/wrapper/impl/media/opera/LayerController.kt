package me.rhunk.snapenhance.data.wrapper.impl.media.opera

import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ReflectionHelper
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

class LayerController(obj: Any?) : AbstractWrapper(obj) {
    val paramMap: ParamMap
        get() {
            val paramMapField: Field = ReflectionHelper.searchFieldTypeInSuperClasses(
                instanceNonNull()::class.java,
                ConcurrentHashMap::class.java
            ) ?: throw RuntimeException("Could not find paramMap field")
            return ParamMap(XposedHelpers.getObjectField(instance, paramMapField.name))
        }
}
