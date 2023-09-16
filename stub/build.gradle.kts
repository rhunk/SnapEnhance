plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}


android {
    namespace = rootProject.ext["applicationId"].toString() + ".stub"
    compileSdk = 34

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
