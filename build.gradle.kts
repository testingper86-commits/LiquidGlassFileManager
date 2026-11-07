buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.5.0") {
            // These plugins are unused: both Android modules contain Java only and disable Kotlin.
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-gradle-plugin")
            exclude(group = "com.google.devtools.ksp", module = "symbol-processing-gradle-plugin")
        }
    }
    // Keep AGP's transitive build-tool libraries on patched, binary-compatible versions.
    configurations.getByName("classpath").resolutionStrategy.force(
        "org.apache.commons:commons-lang3:3.18.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
        "org.apache.httpcomponents:httpmime:4.5.14",
        "com.google.guava:guava:33.7.2-jre",
        "org.bouncycastle:bcprov-jdk18on:1.85",
        "org.bouncycastle:bcpkix-jdk18on:1.85",
        "org.bouncycastle:bcutil-jdk18on:1.85",
        "org.bitbucket.b_c:jose4j:0.9.6",
        "org.jdom:jdom2:2.0.6.1"
    )
}

