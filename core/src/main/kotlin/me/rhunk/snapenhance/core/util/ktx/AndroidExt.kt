package me.rhunk.snapenhance.core.util.ktx

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.Constants
import java.io.InputStream


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

fun Resources.getDimensFloat(name: String): Float {
    return getDimension(getIdentifier(name, "dimen"))
}

fun Resources.getStyledAttributes(name: String, theme: Theme): TypedArray {
    return getIdentifier(name, "attr").let {
        theme.obtainStyledAttributes(intArrayOf(it))
    }
}

fun Resources.getDrawable(name: String, theme: Theme): Drawable {
    return getDrawable(getIdentifier(name, "drawable"), theme)
}

@SuppressLint("MissingPermission")
fun Context.vibrateLongPress() {
    getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
}

@SuppressLint("DiscouragedApi")
fun Context.isDarkTheme(): Boolean {
    return theme.obtainStyledAttributes(
        intArrayOf(resources.getIdentifier("sigColorTextPrimary", "attr", packageName))
    ).getColor(0, 0).let {
        ColorUtils.calculateLuminance(it) > 0.5
    }
}

fun InputStream.toParcelFileDescriptor(coroutineScope: CoroutineScope): ParcelFileDescriptor {
    val pfd = ParcelFileDescriptor.createPipe()
    val fos = ParcelFileDescriptor.AutoCloseOutputStream(pfd[1])

    coroutineScope.launch(Dispatchers.IO) {
        try {
            copyTo(fos)
        } finally {
            close()
            fos.flush()
            fos.close()
        }
    }

    return pfd[0]
}
