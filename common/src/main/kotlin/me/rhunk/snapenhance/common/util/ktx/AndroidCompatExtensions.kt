package me.rhunk.snapenhance.common.util.ktx

import android.content.ClipData
import android.content.Context
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

fun Context.copyToClipboard(data: String, label: String = "Copied Text") {
    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText(label, data))
}