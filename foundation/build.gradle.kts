plugins {
    id("com.android.library")
}

android {
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
