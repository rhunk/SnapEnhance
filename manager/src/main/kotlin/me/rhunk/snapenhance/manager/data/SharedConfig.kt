package me.rhunk.snapenhance.manager.data

import android.content.Context

class SharedConfig(
    context: Context
) {
    private val sharedPreferences = context.getSharedPreferences("snapenhance", Context.MODE_PRIVATE)

    var snapchatPackageName get() = sharedPreferences.getString("snapchatPackageName", "com.snapchat.android")?.takeIf { it.isNotEmpty() }
        set(value) = sharedPreferences.edit().putString("snapchatPackageName", value).apply()

    var snapEnhancePackageName get() = sharedPreferences.getString("snapEnhancePackageName", "me.rhunk.snapenhance")?.takeIf { it.isNotEmpty() }
        set(value) = sharedPreferences.edit().putString("snapEnhancePackageName", value).apply()
}