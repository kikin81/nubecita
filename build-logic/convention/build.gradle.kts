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
    // plugins so the plugin descriptors travel onto the buildscript
    // classpath at runtime — pluginManager.apply("androidx.baselineprofile")
    // inside the convention plugin needs the descriptor metadata
    // resolvable. The other Android plugins (hilt/kotlin/compose) come
    // bundled with AGP 9 and don't need the runtime hop.
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
