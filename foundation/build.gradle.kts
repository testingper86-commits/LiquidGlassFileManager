import com.android.build.api.dsl.LibraryExtension

apply(plugin = "com.android.library")

extensions.configure<LibraryExtension>("android") {
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
    add("implementation", project(":core"))
    add("implementation", "androidx.documentfile:documentfile:1.1.0")
}
