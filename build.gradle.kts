// Each Android subproject resolves its own plugin classpath. Apply the same exclusions
// and patched artifacts to both so the submitted graph cannot retain vulnerable edges.
subprojects {
    if (path == ":app" || path == ":foundation") {
        buildscript {
            repositories {
                google()
                mavenCentral()
            }
            dependencies {
                classpath("com.android.tools.build:gradle:9.5.0") {
                    exclude(group = "org.apache.commons", module = "commons-lang3")
                    exclude(group = "org.apache.httpcomponents", module = "httpclient")
                    exclude(group = "org.apache.httpcomponents", module = "httpmime")
                    exclude(group = "com.google.guava", module = "guava")
                    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
                    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
                    exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
                    exclude(group = "org.bitbucket.b_c", module = "jose4j")
                    exclude(group = "org.jdom", module = "jdom2")
                }
                classpath("org.apache.commons:commons-lang3:3.18.0")
                classpath("org.apache.httpcomponents:httpclient:4.5.14")
                classpath("org.apache.httpcomponents:httpmime:4.5.14")
                classpath("com.google.guava:guava:33.7.2-jre")
                classpath("org.bouncycastle:bcprov-jdk18on:1.85")
                classpath("org.bouncycastle:bcpkix-jdk18on:1.85")
                classpath("org.bouncycastle:bcutil-jdk18on:1.85")
                classpath("org.bitbucket.b_c:jose4j:0.9.6")
                classpath("org.jdom:jdom2:2.0.6.1")
            }
        }
    }
}
