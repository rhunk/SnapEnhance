plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

val nativeName = rootProject.ext.get("buildHash")

android {
    namespace = rootProject.ext["applicationId"].toString() + ".nativelib"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NATIVE_NAME", "\"$nativeName\"")
        packaging {
            jniLibs {
                excludes += "**/libdobby.so"
            }
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DOBFUSCATED_NAME=$nativeName",
                    "-DBUILD_NAMESPACE=${namespace!!.replace(".", "/")}"
                )
            }
            ndk {
                //noinspection ChromeOsAbiSupport
                abiFilters += properties["debug_abi_filters"]?.toString()?.split(",")
                    ?: listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}