pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext resolved via JitPack (no GitHub Packages PAT required)
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "karoo-climber"
include(":app")
