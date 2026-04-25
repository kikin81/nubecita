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
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
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
        register("androidFeature") {
            id = "nubecita.android.feature"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "nubecita.android.application"
            implementationClass = "net.kikin.nubecita.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}
