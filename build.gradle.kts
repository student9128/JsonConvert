import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.kevin"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
//        create("IC", "2024.3")
        intellijIdeaCommunity("2024.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }

        changeNotes = """
           This is a plugin for developers needing convert JSON string into ArkTS/Kotlin/Java model.
           It can be used for all IntelliJPlatform.
          
           
        """.trimIndent()
    }
    pluginVerification {
        ides {
//            recommended()
            create(
                IntelliJPlatformType.IntellijIdeaCommunity,
                "2024.3"
            )
//            select {
//                types = listOf(IntelliJPlatformType.AndroidStudio)
//                channels = listOf(ProductRelease.Channel.RELEASE,ProductRelease.Channel.BETA,ProductRelease.Channel.CANARY)
//                sinceBuild = "243"
//            }
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
