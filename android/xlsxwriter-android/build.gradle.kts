import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec

plugins {
    id("com.android.library")
    id("org.jlleitschuh.gradle.ktlint")
    `maven-publish`
}

group = "ai.botisan"
version = rootProject.extra["xlsxWriterVersion"] as String

val rustRoot = rootProject.layout.projectDirectory.dir("..")
val generatedBindings = layout.buildDirectory.dir("generated/source/uniffi")
val generatedJniLibraries = layout.buildDirectory.dir("generated/jniLibs")
val bindingLibrary = generatedJniLibraries.map { it.file("arm64-v8a/libxlsxwriter.so") }
val rustInputs =
    files(
        rustRoot.file("Cargo.toml"),
        rustRoot.file("Cargo.lock"),
        rustRoot.file("rust-toolchain.toml"),
        rustRoot.file("uniffi.toml"),
        fileTree(rustRoot.dir("src")) { include("**/*.rs") },
    )

val buildRustAndroid by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds the Rust library for every supported Android ABI."
    inputs.files(rustInputs)
    outputs.dir(generatedJniLibraries)
    workingDir(rustRoot)
    doFirst {
        generatedJniLibraries.get().asFile.deleteRecursively()
    }
    commandLine(
        "cargo",
        "ndk",
        "--platform",
        "24",
        "--target",
        "arm64-v8a",
        "--target",
        "armeabi-v7a",
        "--target",
        "x86_64",
        "--target",
        "x86",
        "--output-dir",
        generatedJniLibraries.get().asFile.absolutePath,
        "build",
        "--release",
        "--locked",
        "--lib",
    )
}

val generateUniFfiBindings by tasks.registering(Exec::class) {
    group = "rust"
    description = "Generates Kotlin bindings from the Android Rust library."
    dependsOn(buildRustAndroid)
    inputs.file(bindingLibrary)
    inputs.file(rustRoot.file("uniffi.toml"))
    outputs.dir(generatedBindings)
    workingDir(rustRoot)
    doFirst {
        generatedBindings.get().asFile.deleteRecursively()
    }
    commandLine(
        "cargo",
        "run",
        "--locked",
        "--bin",
        "uniffi-bindgen",
        "--",
        "generate",
        "--library",
        bindingLibrary.get().asFile.absolutePath,
        "--language",
        "kotlin",
        "--out-dir",
        generatedBindings.get().asFile.absolutePath,
        "--no-format",
    )
}

android {
    namespace = "ai.botisan.xlsxwriter"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = false
        buildConfig = false
        resValues = false
    }

    sourceSets.named("main") {
        jniLibs.srcDir(generatedJniLibraries.get().asFile)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addStaticSourceDirectory(generatedBindings.get().asFile.absolutePath)
    }
}

ktlint {
    version.set("1.8.0")
    filter {
        exclude("**/build/**")
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

tasks.named("preBuild") {
    dependsOn(generateUniFfiBindings, buildRustAndroid)
}

tasks.matching { it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(generateUniFfiBindings)
}

tasks.matching { it.name == "publishReleasePublicationToReleaseRepository" }.configureEach {
    doFirst {
        rootProject.layout.buildDirectory
            .dir("repository")
            .get()
            .asFile
            .deleteRecursively()
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "xlsxwriter-android"
                version = project.version.toString()

                pom {
                    name.set("XlsxWriter Android")
                    description.set("Kotlin and UniFFI bindings for the Rust xlsxwriter core.")
                    url.set("https://github.com/botisan-ai/XlsxWriter.swift")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/botisan-ai/XlsxWriter.swift.git")
                        developerConnection.set("scm:git:ssh://github.com/botisan-ai/XlsxWriter.swift.git")
                        url.set("https://github.com/botisan-ai/XlsxWriter.swift")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "release"
                url = uri(rootProject.layout.buildDirectory.dir("repository"))
            }
        }
    }
}

tasks.register<Zip>("generateReleaseRepository") {
    group = "publishing"
    description = "Publishes and zips the Maven repository for a GitHub Release."
    dependsOn("publishReleasePublicationToReleaseRepository")
    from(rootProject.layout.buildDirectory.dir("repository"))
    into("xlsxwriter-android-${project.version}")
    archiveFileName.set("xlsxwriter-android-${project.version}.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
}

tasks.register<Exec>("generateReleaseChecksum") {
    group = "publishing"
    description = "Generates the SHA-256 sidecar for the Maven repository ZIP."
    dependsOn("generateReleaseRepository")
    val archive = rootProject.layout.buildDirectory.file("distributions/xlsxwriter-android-${project.version}.zip")
    inputs.file(archive)
    outputs.file(archive.map { file -> file.asFile.parentFile.resolve("${file.asFile.name}.sha256") })
    commandLine(rootProject.file("checksum-release.sh").absolutePath, archive.get().asFile.absolutePath)
}
