pluginManagement {
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
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
    }
}

rootProject.name = "MushroomAdventure"

include(":app")

// core modules
include(":core:core-logging")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-ui")

// feature modules
include(":feature:feature-task")
include(":feature:feature-checkin")
include(":feature:feature-mushroom")
include(":feature:feature-reward")
include(":feature:feature-milestone")
include(":feature:feature-statistics")

// service modules
include(":service:service-task-generator")
include(":service:service-notification")
