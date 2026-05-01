pluginManagement {
    // БЕЗ ЭТОЙ СТРОКИ СТУДИЯ НИКОГДА НЕ УВИДИТ ТВОИ ПЛАГИНЫ
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "Vetro"
include(":app")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":feature:animelist")
include(":feature:statistics")