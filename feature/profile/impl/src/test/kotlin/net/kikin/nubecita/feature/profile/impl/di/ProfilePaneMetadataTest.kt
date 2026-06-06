package net.kikin.nubecita.feature.profile.impl.di

import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the instance-dependent pane role for [Profile] entries (nubecita-xqp7).
 *
 * The role lives in the (internal) `PaneMetadata` value of the map the
 * `ListDetailSceneStrategy.listPane()` / `detailPane()` factories produce; its
 * runtime class name (`ListMetadata` / `DetailMetadata`) is asserted via
 * reflection since the type itself is library-internal.
 */
class ProfilePaneMetadataTest {
    @Test
    fun `own profile (null handle) is a list-pane anchor`() {
        val metadata = profilePaneMetadata(Profile(handle = null))
        assertEquals("ListMetadata", metadata.values.first()::class.simpleName)
    }

    @Test
    fun `other-user profile (non-null handle) is a detail-pane sub-route`() {
        val metadata = profilePaneMetadata(Profile(handle = "alice.bsky.social"))
        assertEquals("DetailMetadata", metadata.values.first()::class.simpleName)
    }
}
