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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    compileOnly(libs.androidx.activity.ktx)
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.navigation.compose)
    compileOnly(libs.androidx.material.icons.core)
    compileOnly(libs.androidx.material.ripple)
    compileOnly(libs.androidx.material.icons.extended)
    compileOnly(libs.androidx.material3)
}