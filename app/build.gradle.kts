import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.apollo)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

sqldelight {
    databases {
        create("AnimeDatabase") {
            packageName.set("com.example.myapplication.data.local")
        }
    }
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "v3.1.6-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_OWNER", "\"Phnem\"")
        buildConfigField("String", "GITHUB_REPO", "\"Vetra\"")
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-opt-in=dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi"
        )
    }
}

configurations.all {
    resolutionStrategy {
        // Some libraries may bring Kotlin stdlib 2.x with a higher version.
        // This project uses Kotlin 2.1.0, so we force stdlib artifacts to match it.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    }
}

dependencies {
    // 1. Compose: строго через BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // 2. Koin 4: строго через BOM
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // 3. Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 4. Навигация, UI, Стейт
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // 5. AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // 6. Dropbox
    implementation(libs.dropbox.core)
    implementation(libs.dropbox.android)

    // 7. OkHttp, WorkManager
    implementation(libs.okhttp)
    implementation(libs.work.runtime.ktx)

    // 8. SQLDelight
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    // 9. Kotlin Serialization + Immutable collections (Zero Jank)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    // 11. Apollo (core:network; по умолчанию Apollo 4 использует OkHttp на Android)
    implementation(project(":core:network"))
    implementation(libs.apollo.runtime)

    // 12. Markdown renderer (GitHub changelog)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
