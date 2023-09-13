plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".mapper"
    compileSdk = 34

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.coroutines)
    implementation(libs.dexlib2)
    testImplementation(libs.junit)
}