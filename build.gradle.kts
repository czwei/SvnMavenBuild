plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.czwei"
version = "1.0.1"

repositories {
    mavenLocal()
    maven("https://maven.aliyun.com/nexus/content/repositories/central/")
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-compress:1.22")
    implementation("org.dom4j:dom4j:2.1.4")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.1")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf())
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("261.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

}

