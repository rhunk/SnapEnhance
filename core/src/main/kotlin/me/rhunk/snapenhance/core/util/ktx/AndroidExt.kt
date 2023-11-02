package me.rhunk.snapenhance.core.util.ktx

import android.annotation.SuppressLint
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import me.rhunk.snapenhance.common.Constants


@SuppressLint("DiscouragedApi")
fun Resources.getIdentifier(name: String, type: String): Int {
    return getIdentifier(name, type, Constants.SNAPCHAT_PACKAGE_NAME)
}

@SuppressLint("DiscouragedApi")
fun Resources.getId(name: String): Int {
    return getIdentifier(name, "id", Constants.SNAPCHAT_PACKAGE_NAME)
}

fun Resources.getDimens(name: String): Int {
    return getDimensionPixelSize(getIdentifier(name, "dimen"))
}

fun Resources.getStyledAttributes(name: String, theme: Theme): TypedArray {
    return getIdentifier(name, "attr").let {
        theme.obtainStyledAttributes(intArrayOf(it))
    }
}

fun Resources.getDrawable(name: String, theme: Theme): Drawable {
    return getDrawable(getIdentifier(name, "drawable"), theme)
}
