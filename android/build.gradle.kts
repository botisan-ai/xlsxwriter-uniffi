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
    id("com.android.application") version "9.0.1" apply false
    id("com.android.library") version "9.0.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

val xlsxWriterVersion = file("../Cargo.toml")
    .readLines()
    .first { it.trimStart().startsWith("version =") }
    .substringAfter('"')
    .substringBefore('"')

extra["xlsxWriterVersion"] = xlsxWriterVersion

tasks.register<Exec>("verifyReleaseArtifacts") {
    group = "verification"
    description = "Verifies the published Maven module, native libraries, and consumer APK."
    dependsOn(":consumer:assembleDebug", ":xlsxwriter-android:generateReleaseRepository")
    commandLine(
        layout.projectDirectory.file("verify-release-artifacts.sh").asFile.absolutePath,
        layout.buildDirectory.dir("repository").get().asFile.absolutePath,
        xlsxWriterVersion,
        layout.projectDirectory.file("consumer/build/outputs/apk/debug/consumer-debug.apk").asFile.absolutePath,
    )
}
