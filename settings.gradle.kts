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
        // jcifs-ng (SMBクライアント)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "HanaBi"
include(":app")
