package net.kikin.nubecita.core.common.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the shared-element and Coil memory-cache key builders.
 *
 * These keys are a silent contract: a shared element only animates when both
 * halves produce the identical string, and a mismatch raises no error — the
 * transition just does not happen. The cases below pin the two properties the
 * feature depends on: the two halves agree given the same inputs, and keys
 * differ whenever any input differs (surface included, because the same post
 * renders on two surfaces at once in the two-pane list-detail scene).
 */
class SharedImageKeysTest {
    private val postUri = "at://did:plc:abc/app.bsky.feed.post/1"
    private val otherUri = "at://did:plc:abc/app.bsky.feed.post/2"

    @Test
    fun `shared key is stable for the same inputs`() {
        assertEquals(
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 2),
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 2),
        )
    }

    @Test
    fun `shared key differs by surface so one post on two surfaces does not collide`() {
        assertNotEquals(
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 0),
            sharedImageKey(SharedImageSurfaces.POST_DETAIL, postUri, 0),
        )
    }

    @Test
    fun `shared key differs by index`() {
        assertNotEquals(
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 0),
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 1),
        )
    }

    @Test
    fun `shared key differs by post`() {
        assertNotEquals(
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 0),
            sharedImageKey(SharedImageSurfaces.FEED, otherUri, 0),
        )
    }

    @Test
    fun `cache key differs by surface so carousel and full-width requests do not share an entry`() {
        assertNotEquals(
            postImageMemoryCacheKey(SharedImageSurfaces.FEED, postUri, 0),
            postImageMemoryCacheKey(SharedImageSurfaces.POST_DETAIL, postUri, 0),
        )
    }

    @Test
    fun `cache key differs by index`() {
        assertNotEquals(
            postImageMemoryCacheKey(SharedImageSurfaces.FEED, postUri, 0),
            postImageMemoryCacheKey(SharedImageSurfaces.FEED, postUri, 1),
        )
    }

    @Test
    fun `shared and cache keys never collide with each other`() {
        assertNotEquals(
            sharedImageKey(SharedImageSurfaces.FEED, postUri, 0),
            postImageMemoryCacheKey(SharedImageSurfaces.FEED, postUri, 0),
        )
    }

    @Test
    fun `every participating surface has a distinct discriminator`() {
        val surfaces =
            listOf(
                SharedImageSurfaces.FEED,
                SharedImageSurfaces.FEED_VIEW,
                SharedImageSurfaces.POST_DETAIL,
                SharedImageSurfaces.PROFILE,
            )
        val keys = surfaces.map { sharedImageKey(it, postUri, 0) }
        assertEquals(surfaces.size, keys.toSet().size)
    }

    @Test
    fun `none is distinct from every participating surface`() {
        assertNotEquals(SharedImageSurfaces.NONE, SharedImageSurfaces.FEED)
        assertNotEquals(SharedImageSurfaces.NONE, SharedImageSurfaces.FEED_VIEW)
        assertNotEquals(SharedImageSurfaces.NONE, SharedImageSurfaces.POST_DETAIL)
        assertNotEquals(SharedImageSurfaces.NONE, SharedImageSurfaces.PROFILE)
    }
}
