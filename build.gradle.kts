// Each Android subproject resolves its own plugin classpath. Rewrite vulnerable
// transitive coordinates before Gradle resolves or submits the dependency graph.
subprojects {
    if (path == ":app" || path == ":foundation") {
        buildscript {
            repositories {
                google()
                mavenCentral()
            }
            configurations.getByName("classpath").resolutionStrategy.dependencySubstitution {
                substitute(module("org.apache.commons:commons-lang3:3.16.0")).using(module("org.apache.commons:commons-lang3:3.18.0"))
                substitute(module("org.apache.httpcomponents:httpclient:4.5.6")).using(module("org.apache.httpcomponents:httpclient:4.5.14"))
                substitute(module("org.apache.httpcomponents:httpmime:4.5.6")).using(module("org.apache.httpcomponents:httpmime:4.5.14"))
                substitute(module("com.google.guava:guava:33.4.0-jre")).using(module("com.google.guava:guava:33.7.2-jre"))
                substitute(module("com.google.guava:failureaccess:1.0.2")).using(module("com.google.guava:failureaccess:1.0.3"))
                substitute(module("org.bouncycastle:bcprov-jdk18on:1.80.2")).using(module("org.bouncycastle:bcprov-jdk18on:1.85"))
                substitute(module("org.bouncycastle:bcpkix-jdk18on:1.80.2")).using(module("org.bouncycastle:bcpkix-jdk18on:1.85"))
                substitute(module("org.bouncycastle:bcutil-jdk18on:1.80.2")).using(module("org.bouncycastle:bcutil-jdk18on:1.85"))
            }
            dependencies {
                classpath("com.android.tools.build:gradle:9.5.0")
            }
        }
    }
}
