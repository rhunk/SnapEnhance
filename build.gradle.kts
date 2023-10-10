// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

var versionName = "2.0.0"
var versionCode = "0" //"0" for now until stable release

rootProject.ext.set("appVersionName", versionName)
rootProject.ext.set("appVersionCode", versionCode)
rootProject.ext.set("applicationId", "me.rhunk.snapenhance")
rootProject.ext.set("nativeName", properties["custom_native_name"] ?: java.security.SecureRandom().nextLong(1000000000, 99999999999).toString(16))
