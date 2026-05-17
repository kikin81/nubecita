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
include(":core:database")
include(":core:feed-mapping")
include(":core:posting")
include(":core:posts")
include(":core:post-interactions")
include(":core:testing")
include(":core:testing-android")
include(":core:video")
include(":data:models")
include(":designsystem")
include(":feature:chats:api")
include(":feature:chats:impl")
include(":feature:composer:api")
include(":feature:composer:impl")
include(":feature:feed:api")
include(":feature:feed:impl")
include(":feature:login:api")
include(":feature:login:impl")
include(":feature:mediaviewer:api")
include(":feature:mediaviewer:impl")
include(":feature:postdetail:api")
include(":feature:postdetail:impl")
include(":feature:profile:api")
include(":feature:profile:impl")
include(":feature:search:api")
include(":feature:search:impl")
