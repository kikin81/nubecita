package net.kikin.nubecita.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Optionally wires the Compose compiler's stability + recomposition
 * reports into the build, gated on `-PcomposeReports=true`.
 *
 * When enabled, every Compose-using module emits two artifact trees
 * under `<module>/build/compose_compiler/`:
 *
 * - `*-classes.txt` — per-class stability flags (`stable` /
 *   `unstable` + reasons). Threats to 120 Hz scroll show up as
 *   unstable types on the LazyColumn item path.
 * - `*-composables.txt` — per-function annotations (`skippable`,
 *   `restartable`, `readonly`). Non-skippable composables on the
 *   hot path are recomposition-storm candidates.
 *
 * Off by default — incurs a small build-time cost and clutters the
 * build dir. Enable for performance audits and the macrobench
 * workflow (`nubecita-ppj`); leave disabled for routine builds.
 *
 * Invoked from both [AndroidLibraryComposeConventionPlugin] and
 * [AndroidApplicationConventionPlugin] so every Compose-using
 * module participates uniformly.
 */
internal fun Project.configureComposeCompilerReports() {
    val enabled = (findProperty("composeReports") as? String).toBoolean()
    if (!enabled) return

    extensions.findByType<ComposeCompilerGradlePluginExtension>()?.apply {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}
