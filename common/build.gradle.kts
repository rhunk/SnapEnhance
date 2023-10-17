plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".common"
    compileSdk = 34

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.ext["appVersionName"]}\"")
        buildConfigField("int", "VERSION_CODE", "${rootProject.ext["appVersionCode"]}")
        buildConfigField("String", "APPLICATION_ID", "\"${rootProject.ext["applicationId"]}\"")
        buildConfigField("int", "BUILD_DATE", "${System.currentTimeMillis() / 1000}")
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.coroutines)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    implementation(libs.rhino)

    implementation(project(":mapper"))
}