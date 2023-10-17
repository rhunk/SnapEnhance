plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".core"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly(files("libs/LSPosed-api-1.0-SNAPSHOT.jar"))
    implementation(libs.coroutines)
    implementation(libs.recyclerview)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    implementation(libs.rhino)

    implementation(project(":common"))
    implementation(project(":mapper"))
    implementation(project(":native"))
}