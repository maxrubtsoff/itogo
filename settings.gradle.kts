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
        google()
        mavenCentral()
        // Tesseract4Android is published on JitPack (not Maven Central).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BankAggregator"
include(":app")
include(":benchmark")
