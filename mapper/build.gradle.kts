@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("com.android.library")
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "me.rhunk.snapenhance.mapper"
    compileSdk = 33

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