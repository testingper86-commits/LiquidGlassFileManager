import com.android.build.api.dsl.ApplicationExtension

apply(plugin = "com.android.application")

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

extensions.configure<ApplicationExtension>("android") {
    // This module has Java sources only; do not apply the transitive Kotlin compiler plugin.
    enableKotlin = false
    namespace = "com.music.echo.offline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.liquidfiles.offline"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.0"
    }

    signingConfigs {
        if (!releaseStorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!releaseStorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("implementation", project(":core"))
    add("implementation", project(":foundation"))
    add("implementation", "androidx.core:core:1.19.1")
    add("implementation", "androidx.activity:activity:1.13.0")
    add("implementation", "androidx.lifecycle:lifecycle-runtime:2.10.0")
    add("implementation", "androidx.documentfile:documentfile:1.1.0")
    add("implementation", "androidx.webkit:webkit:1.18.0")
    add("testImplementation", "junit:junit:4.13.2")
}

