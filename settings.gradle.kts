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
        // de.robv.android.xposed:api is not on Maven Central; api.xposed.info is
        // the canonical (and often the only) host of artifacts 82.
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "nothing-essential-camera"
include(":app")
