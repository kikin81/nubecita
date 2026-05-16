plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.search.impl"
}

dependencies {
    api(project(":feature:search:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:feed-mapping"))
    // Tap-to-open the post hit pushes a PostDetailRoute onto the MainShell
    // back stack. The api module ships just the NavKey — :feature:search:impl
    // never depends on :impl, matching the Chats / Feed / Profile pattern.
    implementation(project(":feature:postdetail:api"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
