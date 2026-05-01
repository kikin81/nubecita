pluginManagement {
    includeBuild("build-logic")
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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Nubecita"
include(":app")
include(":core:auth")
include(":core:common")
include(":core:testing")
include(":core:testing-android")
include(":data:models")
include(":designsystem")
include(":feature:chats:api")
include(":feature:feed:api")
include(":feature:feed:impl")
include(":feature:login:api")
include(":feature:login:impl")
include(":feature:profile:api")
include(":feature:search:api")
