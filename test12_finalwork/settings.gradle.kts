pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://csspeechstorage.blob.core.windows.net/maven/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://csspeechstorage.blob.core.windows.net/maven/") }
    }
}

rootProject.name = "create-part2"
include(":app")
