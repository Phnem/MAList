import java.util.Properties

plugins {
    id("vetro.android.library.plain")
    id("vetro.kotlin.serialization")
    alias(libs.plugins.apollo)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.phnem.vetro.network"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"${localProperties.getProperty("TMDB_API_KEY", "")}\""
        )
    }
}

apollo {
    service("anilist") {
        packageName.set("com.example.myapplication.network.anilist")
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}

dependencies {
    api(platform(libs.ktor.bom))
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)
    api(libs.apollo.runtime)

    implementation(libs.ktor.client.logging)
    implementation(libs.okhttp)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}
