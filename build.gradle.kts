// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

rootProject.ext.set("appVersionName", "2.0.0")
rootProject.ext.set("appVersionCode", 10)
rootProject.ext.set("applicationId", "me.rhunk.snapenhance")
rootProject.ext.set("nativeName", java.security.SecureRandom().nextLong(1000000000, 99999999999).toString(16))

true // Needed to make the Suppress annotation work for the plugins block