plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.login.impl"
}

dependencies {
    api(project(":feature:login:api"))

    implementation(project(":core:auth"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
