package me.rhunk.snapenhance.common.util.ktx

import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.os.Build

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, ApplicationInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, flags)
    }
