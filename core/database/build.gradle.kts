import com.android.build.api.variant.LibraryAndroidComponentsExtension

plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.room)
}

android {
    namespace = "net.kikin.nubecita.core.database"
}

// Pick up the committed schemas directory as androidTest assets so
// MigrationTestHelper (added when migrations exist) can locate them.
// Uses the AGP 9 variant API instead of the legacy
// `android.sourceSets["androidTest"].assets.srcDir(...)` which fails with a
// classloader-mismatch ClassCastException between the new
// `com.android.build.api.dsl.AndroidLibrarySourceSet` and the legacy
// `com.android.build.gradle.api.AndroidLibrarySourceSet`.
extensions.configure<LibraryAndroidComponentsExtension> {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory(
            "$projectDir/schemas",
        )
    }
}

dependencies {
    api(project(":data:models"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
