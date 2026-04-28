import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.nubecita.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
    jacoco
}

android {
    namespace = "net.kikin.nubecita"

    defaultConfig {
        applicationId = "net.kikin.nubecita"
        versionCode = 1
        versionName = project.property("version").toString()

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
                releaseNotes = System.getenv("APP_DISTRIBUTION_RELEASE_NOTES") ?: ""
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
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:feed:impl"))
    implementation(project(":feature:login:api"))
    implementation(project(":feature:login:impl"))
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.firebase.appcheck.debug)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.register("publish") {
    description = "No-op publish task to satisfy semantic-release verification in CI"
    group = "publishing"
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter =
        listOf(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*\$Lambda\$*.*",
            "**/*\$inlined\$*.*",
        )

    val debugTree =
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/*.exec", "outputs/unit_test_code_coverage/**/*.exec")
        },
    )
}
