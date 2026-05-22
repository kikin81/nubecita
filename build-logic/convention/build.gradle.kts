plugins {
    `kotlin-dsl`
}

group = "net.kikin.nubecita.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    // implementation (not compileOnly) for baselineprofile + benchmark
    // plugins so their descriptors are on the convention plugin's
    // runtime classpath — pluginManager.apply("androidx.baselineprofile")
    // executed from within AndroidBenchmarkConventionPlugin needs to
    // resolve the descriptor at runtime, and compileOnly deps are not
    // exposed there. The other Android plugins (hilt/kotlin/compose)
    // can stay compileOnly because they are applied at the consumer
    // project's `plugins { }` block via `alias(...)`, which uses
    // Gradle's plugin-marker resolution (pluginManagement) rather than
    // the convention plugin's own runtime classpath.
    implementation(libs.androidx.baselineprofile.gradlePlugin)
    implementation(libs.androidx.benchmark.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.screenshot.gradlePlugin)
    compileOnly(libs.sortDependencies.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "nubecita.android.library"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "nubecita.android.library.compose"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "nubecita.android.hilt"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidJacoco") {
            id = "nubecita.android.jacoco"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidJacocoConventionPlugin"
        }
        register("androidFeature") {
            id = "nubecita.android.feature"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "nubecita.android.application"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidBenchmark") {
            id = "nubecita.android.benchmark"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidBenchmarkConventionPlugin"
        }
        register("androidRoom") {
            id = "nubecita.android.room"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidRoomConventionPlugin"
        }
    }
}
