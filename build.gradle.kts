// Plugins live on the root classpath so :core and :app share one Kotlin Gradle plugin.
// -PcoreOnly skips AGP: the pure-Kotlin module then builds without Google Maven.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        val kotlinVersion = "2.1.0"
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (!gradle.startParameter.projectProperties.containsKey("coreOnly")) {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}
