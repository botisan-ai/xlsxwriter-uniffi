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
val hostLibraryName =
    if (System.getProperty("os.name").startsWith("Mac")) "libxlsxwriter.dylib" else "libxlsxwriter.so"
val hostLibrary = rustRoot.file("target/release/$hostLibraryName")
val rustInputs =
    files(
        rustRoot.file("Cargo.toml"),
        rustRoot.file("Cargo.lock"),
        rustRoot.file("rust-toolchain.toml"),
        rustRoot.file("uniffi.toml"),
        fileTree(rustRoot.dir("src")) { include("**/*.rs") },
    )

// UniFFI needs the symbol table that Android release builds may strip, so binding
// generation uses a host library. The same library supports host-JVM unit tests.
val buildRustHost by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds the host Rust library for bindings and JVM tests."
    inputs.files(rustInputs)
    outputs.file(hostLibrary)
    workingDir(rustRoot)
    commandLine("cargo", "build", "--release", "--locked", "--lib")
}

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
        "x86_64",
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
    description = "Generates Kotlin bindings from the host Rust library."
    dependsOn(buildRustHost)
    inputs.file(hostLibrary)
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
        hostLibrary.asFile.absolutePath,
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
        isCoreLibraryDesugaringEnabled = true
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.annotation:annotation:1.9.1")
    api("net.java.dev.jna:jna:5.19.1@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.java.dev.jna:jna:5.19.1")

    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("javax.xml.stream:stax-api:1.0-2")
    androidTestImplementation("org.dhatim:fastexcel-reader:0.20.2")
}

tasks.named("preBuild") {
    dependsOn(generateUniFfiBindings, buildRustAndroid)
}

tasks.matching { it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(generateUniFfiBindings)
}

tasks.withType<Test>().configureEach {
    dependsOn(buildRustHost)
    systemProperty("jna.library.path", hostLibrary.asFile.parentFile.absolutePath)
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
                    url.set("https://github.com/botisan-ai/xlsxwriter-uniffi")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/botisan-ai/xlsxwriter-uniffi.git")
                        developerConnection.set("scm:git:ssh://github.com/botisan-ai/xlsxwriter-uniffi.git")
                        url.set("https://github.com/botisan-ai/xlsxwriter-uniffi")
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
    archiveFileName.set("xlsxwriter-android-${project.version}-maven.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
}

tasks.register<Copy>("generateReleaseAar") {
    group = "publishing"
    description = "Copies the published AAR into the GitHub Release distribution directory."
    dependsOn("publishReleasePublicationToReleaseRepository")
    from(
        rootProject.layout.buildDirectory.file(
            "repository/ai/botisan/xlsxwriter-android/${project.version}/xlsxwriter-android-${project.version}.aar",
        ),
    )
    into(rootProject.layout.buildDirectory.dir("distributions"))
}

tasks.register<Exec>("generateReleaseChecksums") {
    group = "publishing"
    description = "Generates SHA-256 sidecars for the Android release artifacts."
    dependsOn("generateReleaseRepository", "generateReleaseAar")
    val releaseDirectory = rootProject.layout.buildDirectory.dir("distributions")
    val mavenArchive = releaseDirectory.map { it.file("xlsxwriter-android-${project.version}-maven.zip") }
    val aar = releaseDirectory.map { it.file("xlsxwriter-android-${project.version}.aar") }
    inputs.files(mavenArchive, aar)
    outputs.files(
        mavenArchive.map { file -> file.asFile.parentFile.resolve("${file.asFile.name}.sha256") },
        aar.map { file -> file.asFile.parentFile.resolve("${file.asFile.name}.sha256") },
    )
    commandLine(
        rootProject.file("checksum-release.sh").absolutePath,
        mavenArchive.get().asFile.absolutePath,
        aar.get().asFile.absolutePath,
    )
}
