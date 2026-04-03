plugins {
    id("vetro.android.library")
    id("vetro.sqldelight")
    id("vetro.kotlin.serialization")
}

android {
    namespace = "com.phnem.vetro.database"
}

dependencies {
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.serialization.json)
}
