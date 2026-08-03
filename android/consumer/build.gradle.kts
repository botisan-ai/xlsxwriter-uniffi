plugins {
    id("com.android.application")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "ai.botisan.xlsxwriter.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.botisan.xlsxwriter.consumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("ai.botisan:xlsxwriter-android:${rootProject.extra["xlsxWriterVersion"]}")
}

ktlint {
    version.set("1.8.0")
}

tasks.named("preBuild") {
    dependsOn(":xlsxwriter-android:publishReleasePublicationToReleaseRepository")
}
