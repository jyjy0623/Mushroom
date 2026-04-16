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
        // CI 环境优先使用官方源，避免第三方镜像偶发超时
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
    }
}

rootProject.name = "MushroomAdventure"

include(":app")

// core modules
include(":core:core-logging")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-ui")
include(":core:core-network")

// feature modules
include(":feature:feature-task")
include(":feature:feature-checkin")
include(":feature:feature-mushroom")
include(":feature:feature-reward")
include(":feature:feature-milestone")
include(":feature:feature-statistics")
include(":feature:feature-game")
include(":feature:feature-account")

// service modules
include(":service:service-task-generator")
include(":service:service-notification")

// lint rules
include(":lint-rules")
