package me.rhunk.snapenhance.manager.data

import android.content.Context
import me.rhunk.snapenhance.manager.BuildConfig

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

    var snapEnhancePackageName get() = sharedPreferences.getString("snapEnhancePackageName", BuildConfig.APPLICATION_ID)?.takeIf { it.isNotEmpty() } ?: BuildConfig.APPLICATION_ID
        set(value) = sharedPreferences.edit().putString("snapEnhancePackageName", value).apply()
    var enableRepackage get() = sharedPreferences.getBoolean("enableRepackage", false)
        set(value) = sharedPreferences.edit().putBoolean("enableRepackage", value).apply()

    var useRootInstaller get() = sharedPreferences.getBoolean("useRootInstaller", false)
        set(value) = sharedPreferences.edit().putBoolean("useRootInstaller", value).apply()

    var obfuscateLSPatch get() = sharedPreferences.getBoolean("obfuscateLSPatch", false)
        set(value) = sharedPreferences.edit().putBoolean("obfuscateLSPatch", value).apply()
}