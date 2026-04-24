package net.kikin.nubecita.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Root version catalog ("libs") shared between the main build and the
 * build-logic composite. Resolved via the VersionCatalogsExtension; the
 * composite's settings.gradle.kts wires `../gradle/libs.versions.toml`
 * into its own resolution.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
