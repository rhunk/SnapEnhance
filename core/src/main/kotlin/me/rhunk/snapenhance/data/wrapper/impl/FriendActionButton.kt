package me.rhunk.snapenhance.data.wrapper.impl

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import me.rhunk.snapenhance.SnapEnhance
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class FriendActionButton(
    obj: View
) : AbstractWrapper(obj) {
    private val iconDrawableContainer by lazy {
        instanceNonNull().javaClass.declaredFields.first { it.type != Int::class.javaPrimitiveType }[instanceNonNull()]
    }

    private val setIconDrawableMethod by lazy {
        iconDrawableContainer.javaClass.declaredMethods.first {
            it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Drawable::class.java &&
                    it.name != "invalidateDrawable" &&
                    it.returnType == Void::class.javaPrimitiveType
        }
    }

    fun setIconDrawable(drawable: Drawable) {
        setIconDrawableMethod.invoke(iconDrawableContainer, drawable)
    }

    companion object {
        fun new(context: Context): FriendActionButton {
            val instance = SnapEnhance.classLoader.loadClass("com.snap.profile.shared.view.FriendActionButton")
                .getConstructor(Context::class.java, AttributeSet::class.java)
                .newInstance(context, null) as View
            return FriendActionButton(instance)
        }
    }
}