package net.kikin.nubecita.buildlogic

import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Applies the Room Gradle plugin and KSP, configures schema export to
 * `$projectDir/schemas`, requests Kotlin code generation from KSP, and
 * wires the Room runtime + ktx libraries plus the Room KSP compiler.
 *
 * Apply alongside [AndroidLibraryConventionPlugin] and
 * [AndroidHiltConventionPlugin]; KSP is idempotent so double-applying
 * via the Hilt convention is safe.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            dependencies {
                "implementation"(libs.findLibrary("room-runtime").get())
                "implementation"(libs.findLibrary("room-ktx").get())
                "ksp"(libs.findLibrary("room-compiler").get())
            }
        }
    }
}
