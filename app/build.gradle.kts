plugins {
    id("com.android.application")
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

android {
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
    implementation(project(":core"))
    implementation(project(":foundation"))
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.webkit:webkit:1.18.0")
    testImplementation("junit:junit:4.13.2")
}

