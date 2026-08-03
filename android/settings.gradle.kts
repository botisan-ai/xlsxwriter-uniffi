pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "xlsxWriterLocal"
            url = uri(rootDir.resolve("build/repository"))
            content {
                includeGroup("ai.botisan")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "XlsxWriterAndroid"
include(":xlsxwriter-android")
include(":consumer")
