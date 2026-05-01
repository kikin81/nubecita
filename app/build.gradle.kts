import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.nubecita.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "net.kikin.nubecita"

    defaultConfig {
        applicationId = "net.kikin.nubecita"
        versionCode = 1
        versionName = project.property("version").toString()

        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest tests
        // can boot HiltTestApplication. :app currently has no @HiltAndroidTest
        // tests itself (FirebaseInitTest + MainShellPersistenceTest are plain
        // AndroidJUnit4); the runner stays Hilt-compatible for forthcoming
        // cross-feature E2E tests.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"

        buildConfigField(
            type = "String",
            name = "OAUTH_CLIENT_METADATA_URL",
            value = "\"https://kikin81.github.io/nubecita/oauth/client-metadata.json\"",
        )
    }

    buildTypes {
        debug {
            firebaseAppDistribution {
                groups = "internal-testers"
                // providers.environmentVariable(...) is configuration-cache-aware:
                // Gradle tracks the env var as an input and invalidates the cache
                // when it changes between runs, unlike System.getenv() which bakes
                // the value into the cache silently.
                releaseNotes = providers.environmentVariable("APP_DISTRIBUTION_RELEASE_NOTES").orElse("").get()
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(platform(libs.coil.bom))
    implementation(platform(libs.firebase.bom))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":designsystem"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:feed:impl"))
    implementation(project(":feature:login:api"))
    implementation(project(":feature:login:impl"))
    implementation(project(":feature:profile:api"))
    implementation(project(":feature:search:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.firebase.appcheck.debug)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.ext.junit)
    kspAndroidTest(libs.hilt.android.compiler)
}

tasks.register("publish") {
    description = "No-op publish task to satisfy semantic-release verification in CI"
    group = "publishing"
}
