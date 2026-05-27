plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.notifications.impl"
}

dependencies {
    api(project(":feature:notifications:api"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
