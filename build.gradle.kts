// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

rootProject.ext.set("appVersionName", "1.1.0")
rootProject.ext.set("appVersionCode", 7)
rootProject.ext.set("applicationId", "me.rhunk.snapenhance")

true // Needed to make the Suppress annotation work for the plugins block