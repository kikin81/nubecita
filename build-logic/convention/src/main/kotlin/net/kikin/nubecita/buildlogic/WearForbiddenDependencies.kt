package net.kikin.nubecita.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Fails the Wear build when a phone-only SDK reaches the watch's release
 * runtime classpath (nubecita-v754).
 *
 * Jetpack XR once leaked onto the watch through a shared module
 * (`:core:common`): R8 then failed on missing XR system classes, ARCore's
 * install activity landed in the watch manifest, and ~29 MB of native
 * libraries R8 can't strip rode along (nubecita-2mnp). Checking the resolved
 * graph catches that through any path, whichever module adds it next.
 *
 * Runs before every Wear build (`preBuild`), so CI's debug build fails too,
 * not only the release upload. The graph is resolved lazily, so the
 * configuration cache stays valid.
 */
internal fun Project.registerWearForbiddenDependencyCheck() {
    val checkTask =
        tasks.register("checkWearForbiddenDependencies") {
            group = "verification"
            description = "Fails if a phone-only SDK (Jetpack XR, ARCore) is on the Wear release runtime classpath."
            val graph =
                configurations
                    .named(CHECKED_CONFIGURATION)
                    .flatMap { it.incoming.resolutionResult.rootComponent }
            doLast {
                val offenders = forbiddenModules(graph.get())
                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "Phone-only dependencies reached the Wear app ($CHECKED_CONFIGURATION):\n" +
                            offenders.joinToString("\n") { "  - $it" } +
                            "\nRun ':wear:app:dependencies --configuration $CHECKED_CONFIGURATION' to find the module " +
                            "that brings them in, and keep them out of anything the watch depends on.",
                    )
                }
            }
        }
    tasks.named("preBuild") { dependsOn(checkTask) }
}

private fun forbiddenModules(root: ResolvedComponentResult): Set<String> {
    val offenders = sortedSetOf<String>()
    val seen = mutableSetOf<ResolvedComponentResult>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (!seen.add(component)) continue
        val id = component.id as? ModuleComponentIdentifier
        if (id != null && FORBIDDEN_GROUPS.any { id.group == it || id.group.startsWith("$it.") }) {
            offenders += "${id.group}:${id.module}"
        }
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending += it.selected }
    }
    return offenders
}

/** What ships to the Play Store; bench and debug resolve the same graph. */
private const val CHECKED_CONFIGURATION = "productionReleaseRuntimeClasspath"

/** Jetpack XR (`androidx.xr.*`) and ARCore / the XR scene engine (`com.google.ar.*`). */
private val FORBIDDEN_GROUPS = listOf("androidx.xr", "com.google.ar")
