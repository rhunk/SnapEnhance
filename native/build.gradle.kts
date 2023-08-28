@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

val nativeName = System.nanoTime().toString(16)

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
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
