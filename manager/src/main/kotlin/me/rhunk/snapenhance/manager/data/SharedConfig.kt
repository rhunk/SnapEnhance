package me.rhunk.snapenhance.manager.data

import android.content.Context

class SharedConfig(
    context: Context
) {
    private val sharedPreferences = context.getSharedPreferences("snapenhance", Context.MODE_PRIVATE)

    val apkCache by lazy {
        context.cacheDir.resolve("snapchat_apk_cache").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    var snapchatPackageName get() = sharedPreferences.getString("snapchatPackageName", "com.snapchat.android")?.takeIf { it.isNotEmpty() } ?: "com.snapchat.android"
        set(value) = sharedPreferences.edit().putString("snapchatPackageName", value).apply()

    var snapEnhancePackageName get() = sharedPreferences.getString("snapEnhancePackageName", "me.rhunk.snapenhance")?.takeIf { it.isNotEmpty() } ?: "me.rhunk.snapenhance"
        set(value) = sharedPreferences.edit().putString("snapEnhancePackageName", value).apply()
}