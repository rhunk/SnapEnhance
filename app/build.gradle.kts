import com.android.build.gradle.internal.api.BaseVariantOutputImpl

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "me.rhunk.snapenhance"
    compileSdk = 34

    buildFeatures {
        aidl = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    defaultConfig {
        applicationId = rootProject.ext["applicationId"].toString()
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 33
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    flavorDimensions += "abi"

    productFlavors {
        create("armv8") {
            ndk {
                abiFilters.add("arm64-v8a")
            }

            dimension = "abi"
        }

        create("armv7") {
            ndk {
                abiFilters.add("armeabi-v7a")
            }
            packaging {
                jniLibs {
                    excludes += "**/*_neon.so"
                }
            }
            dimension = "abi"
        }
    }

    properties["debug_flavor"]?.let {
        android.productFlavors[it.toString()].setIsDefault(true)
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }.forEach { variant ->
            variant.outputFileName = "app-${rootProject.ext["appVersionName"]}-${variant.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)

    debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
    implementation(kotlin("reflect"))
}

afterEvaluate {
    properties["debug_assemble_task"]?.let { tasks.named(it.toString()) }?.orNull?.doLast {
        runCatching {
            val apkDebugFile = android.applicationVariants.find { it.buildType.name == "debug" && it.flavorName == properties["debug_flavor"] }?.outputs?.first()?.outputFile ?: return@doLast
            exec {
                commandLine("adb", "shell", "am", "force-stop", "com.snapchat.android")
            }
            exec {
                commandLine("adb", "install", "-r", "-d", apkDebugFile.absolutePath)
            }
            exec {
                commandLine("adb", "shell", "am", "start", "com.snapchat.android")
            }
        }
    }
}