plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.search.impl"
}

dependencies {
    api(project(":feature:search:api"))

    implementation(project(":core:common"))
    implementation(project(":core:database"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
