import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

<<<<<<< HEAD
=======
val appVersionName = "1.2.4"
val appVersionCode = 12

>>>>>>> origin
android {
    namespace = rootProject.ext["applicationId"].toString()
    compileSdk = 34

    buildFeatures {
        aidl = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }

    defaultConfig {
        applicationId = rootProject.ext["applicationId"].toString()
        minSdk = 28
        targetSdk = 34

        versionCode = rootProject.ext["appVersionCode"].toString().toInt()
        versionName = rootProject.ext["appVersionName"].toString()
        multiDexEnabled = true
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles += file("proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    flavorDimensions += "abi"

    //noinspection ChromeOsAbiSupport
    productFlavors {
        packaging {
            jniLibs {
                excludes += "**/*_neon.so"
            }
            resources {
                excludes += "DebugProbesKt.bin"
                excludes += "okhttp3/internal/publicsuffix/**"
                excludes += "META-INF/*.version"
                excludes += "META-INF/services/**"
                excludes += "META-INF/*.kotlin_builtins"
                excludes += "META-INF/*.kotlin_module"
            }
        }

        create("core") {
            dimension = "abi"
        }

        create("armv8") {
            ndk {
                abiFilters += "arm64-v8a"
            }
            dimension = "abi"
        }

        create("armv7") {
            ndk {
                abiFilters += "armeabi-v7a"
            }
            dimension = "abi"
        }

        create("all") {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            dimension = "abi"
        }
    }

    properties["debug_flavor"]?.let {
        android.productFlavors.find { it.name == it.toString()}?.setIsDefault(true)
    }

    applicationVariants.all {
<<<<<<< HEAD
        outputs.map { it as BaseVariantOutputImpl }.forEach { outputVariant ->
            outputVariant.outputFileName = when {
                name.startsWith("core") -> "core.apk"
                else -> "snapenhance_${rootProject.ext["appVersionName"]}-${outputVariant.name}.apk"
            }
=======
        outputs.map { it as BaseVariantOutputImpl }.forEach { variant ->
            variant.outputFileName = "SnapEnhance-${appVersionName}-${variant.name}.apk"
>>>>>>> origin
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

androidComponents {
    onVariants(selector().withFlavor("abi", "core")) {
        it.packaging.jniLibs.apply {
            pickFirsts.set(listOf("**/lib${rootProject.ext["nativeName"]}.so"))
            excludes.set(listOf("**/*.so"))
        }
    }
}

dependencies {
    fun fullImplementation(dependencyNotation: Any) {
        compileOnly(dependencyNotation)
        for (flavorName in listOf("armv8", "armv7", "all")) {
            dependencies.add("${flavorName}Implementation", dependencyNotation)
        }
    }

    implementation(project(":core"))
    implementation(libs.androidx.documentfile)
    implementation(libs.gson)
    implementation(libs.ffmpeg.kit)
    implementation(libs.osmdroid.android)
    implementation(libs.rhino)
    implementation(libs.androidx.activity.ktx)
    fullImplementation(libs.bcprov.jdk18on)
    fullImplementation(libs.androidx.navigation.compose)
    fullImplementation(libs.androidx.material.icons.core)
    fullImplementation(libs.androidx.material.ripple)
    fullImplementation(libs.androidx.material.icons.extended)
    fullImplementation(libs.androidx.material3)
    fullImplementation(libs.coil.compose)
    fullImplementation(libs.coil.video)
    fullImplementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
}

afterEvaluate {
    properties["debug_assemble_task"]?.let { tasks.findByName(it.toString()) }?.doLast {
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
