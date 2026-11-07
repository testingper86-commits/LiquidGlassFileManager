plugins {
    id("com.android.library")
}

android {
    // This module has Java sources only; do not apply the transitive Kotlin compiler plugin.
    enableKotlin = false
    namespace = "com.music.echo.foundation"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.documentfile:documentfile:1.1.0")
}
