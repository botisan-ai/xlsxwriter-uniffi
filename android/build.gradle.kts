buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    id("com.android.library") version "9.0.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}
