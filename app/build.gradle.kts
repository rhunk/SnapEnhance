import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.configurationcache.extensions.capitalized
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("kotlin-parcelize")
}

android {
    namespace = rootProject.ext["applicationId"].toString()
    compileSdk = 34

    buildFeatures {
        aidl = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    defaultConfig {
        applicationId = rootProject.ext["applicationId"].toString()
        versionCode = rootProject.ext["appVersionCode"].toString().toInt()
        versionName = rootProject.ext["appVersionName"].toString()
        minSdk = 28
        targetSdk = 34
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles += file("proguard-rules.pro")
        }
        debug {
            (properties["debug_flavor"] == null).also {
                isDebuggable = !it
                isMinifyEnabled = it
                isShrinkResources = it
            }
            proguardFiles += file("proguard-rules.pro")
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
        outputs.map { it as BaseVariantOutputImpl }.forEach { outputVariant ->
            outputVariant.outputFileName = when {
                name.startsWith("core") -> "core.apk"
                else -> "snapenhance_${rootProject.ext["appVersionName"]}-${outputVariant.name}.apk"
            }
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

androidComponents {
    onVariants(selector().withFlavor("abi", "core")) {
        it.packaging.jniLibs.apply {
            pickFirsts.set(listOf("**/lib${rootProject.ext["buildHash"]}.so"))
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
    implementation(project(":common"))
    implementation(libs.androidx.documentfile)
    implementation(libs.gson)
    implementation(libs.ffmpeg.kit)
    implementation(libs.osmdroid.android)
    implementation(libs.rhino)
    implementation(libs.androidx.activity.ktx)
    fullImplementation(platform(libs.androidx.compose.bom))
    fullImplementation(libs.bcprov.jdk18on)
    fullImplementation(libs.androidx.navigation.compose)
    fullImplementation(libs.androidx.material.icons.core)
    fullImplementation(libs.androidx.material.ripple)
    fullImplementation(libs.androidx.material.icons.extended)
    fullImplementation(libs.androidx.material3)
    fullImplementation(libs.coil.compose)
    fullImplementation(libs.coil.video)
    fullImplementation(libs.androidx.ui.tooling.preview)
    properties["debug_flavor"]?.let {
        debugImplementation(libs.androidx.ui.tooling)
    }
}

afterEvaluate {
    properties["debug_flavor"]?.toString()?.let { tasks.findByName("install${it.capitalized()}Debug") }?.doLast {
        runCatching {
            val devices = ByteArrayOutputStream().also {
                exec {
                    commandLine("adb", "devices")
                    standardOutput = it
                }
            }.toString().lines().drop(1).mapNotNull {
                line -> line.split("\t").firstOrNull()?.takeIf { it.isNotEmpty() }
            }

            runBlocking {
                devices.forEach { device ->
                    launch {
                        exec {
                            commandLine("adb", "-s", device, "shell", "am", "force-stop", properties["debug_package_name"])
                        }
                        delay(500)
                        exec {
                            commandLine("adb", "-s", device, "shell", "am", "start", properties["debug_package_name"])
                        }
                    }
                }
            }
        }
    }
}