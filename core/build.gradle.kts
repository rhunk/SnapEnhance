@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}
android {
    namespace = rootProject.ext["applicationId"].toString() + ".core"
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
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.register("getVersion") {
    doLast {
        val versionFile = File("app/build/version.txt")
        versionFile.writeText(android.defaultConfig.versionName.toString())
    }
}

dependencies {
    compileOnly(files("libs/LSPosed-api-1.0-SNAPSHOT.jar"))
    implementation(libs.coroutines)
    implementation(libs.kotlin.reflect)
    implementation(libs.recyclerview)
    implementation(libs.gson)
    implementation(libs.ffmpeg.kit)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)

    implementation(project(":mapper"))
    implementation(project(":native"))
}