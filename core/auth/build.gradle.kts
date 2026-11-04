plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.auth"
    // Needed so HttpClientModule can gate Ktor's request/response Logging plugin
    // on BuildConfig.DEBUG. Defense in depth: even if a release Timber tree is
    // planted later (Crashlytics/Sentry), the install itself is build-type-gated
    // so DPoP/Authorization headers can never reach release logcat or remote
    // crash reports — independent of which Timber trees are planted at runtime.
    buildFeatures.buildConfig = true

    // Opt-in auth test harness (nubecita-zh7p): `-PauthTestHarness=true`.
    //
    // A source set rather than a BuildConfig flag, deliberately. A runtime (or
    // even R8-folded) flag still compiles a "wipe this user's session" code
    // path into the Play APK and leaves its absence resting on the optimizer
    // behaving as expected. Gating the source directory means the class does
    // not exist in any build that did not ask for it — no stripping to verify,
    // nothing to get wrong.
    //
    // Attached to `production`, not `main`: the harness drives the real
    // encrypted session store, which the `bench` flavor replaces with fakes.
    if (providers.gradleProperty("authTestHarness").orNull.toBoolean()) {
        sourceSets.getByName("production").kotlin.srcDir("src/authHarness/kotlin")
    }

    // The `environment` flavor dimension splits the production AuthBindingsModule
    // (real DPoP-signed XrpcClient, Tink-encrypted OAuthSessionStore, AtOAuth flow
    // orchestrator) from a bench-flavor parallel that binds in-process fakes. The
    // `:app` module's `bench` flavor consumes the matching variant via the
    // missingDimensionStrategy plumbing in AndroidLibraryConventionPlugin — see
    // `bd show nubecita-crmi.6` for the broader scope.
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    api(libs.atproto.oauth)
    api(libs.atproto.runtime)

    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.tink)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)
    implementation(libs.tink.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
