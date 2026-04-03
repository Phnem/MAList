plugins {
    id("vetro.android.library")
    id("vetro.compose")
}

android {
    namespace = "com.phnem.vetro.feature.statistics"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
