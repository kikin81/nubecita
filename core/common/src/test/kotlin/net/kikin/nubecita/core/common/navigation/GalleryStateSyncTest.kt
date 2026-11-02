package net.kikin.nubecita.core.common.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GalleryStateSync] and its [GalleryStateSyncSaver].
 *
 * Constructed directly rather than through `rememberGalleryStateSync` — the
 * holder is plain snapshot state with no composition dependency, and the
 * `rememberSaveable` wiring is exercised here through the saver's
 * save/restore pair, which is the part that can actually be wrong.
 */
class GalleryStateSyncTest {
    private val postUri = "at://did:plc:abc/app.bsky.feed.post/1"
    private val otherUri = "at://did:plc:abc/app.bsky.feed.post/2"

    @Test
    fun `peek returns the index set for the same post`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 3)
        assertEquals(3, sync.peekActiveIndex(postUri))
    }

    @Test
    fun `peek does not consume so repeated reads are stable`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 2)
        assertEquals(2, sync.peekActiveIndex(postUri))
        assertEquals(2, sync.peekActiveIndex(postUri))
    }

    @Test
    fun `peek returns null for a different post`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 1)
        assertNull(sync.peekActiveIndex(otherUri))
    }

    @Test
    fun `consume returns the index once then clears it`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 4)
        assertEquals(4, sync.consumeActiveIndex(postUri))
        assertNull(sync.consumeActiveIndex(postUri))
    }

    @Test
    fun `consume for a different post leaves the pending hand-off intact`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 5)
        assertNull(sync.consumeActiveIndex(otherUri))
        assertEquals(5, sync.peekActiveIndex(postUri))
    }

    @Test
    fun `setting a second post replaces the pending hand-off rather than accumulating`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 1)
        sync.setActiveIndex(otherUri, 7)
        assertNull(sync.peekActiveIndex(postUri))
        assertEquals(7, sync.peekActiveIndex(otherUri))
    }

    @Test
    fun `blank post uri is ignored`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex("", 3)
        assertNull(sync.peekActiveIndex(""))
    }

    @Test
    fun `clear drops the pending hand-off`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 6)
        sync.clear()
        assertNull(sync.peekActiveIndex(postUri))
    }

    @Test
    fun `index zero survives consume as a real value and not as absent`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 0)
        assertEquals(0, sync.consumeActiveIndex(postUri))
    }

    @Test
    fun `saver round-trips a pending hand-off`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 8)
        val restored = restore(save(sync))
        assertEquals(8, restored.peekActiveIndex(postUri))
    }

    @Test
    fun `saver round-trips an empty holder`() {
        val restored = restore(save(GalleryStateSync()))
        assertNull(restored.peekActiveIndex(postUri))
    }

    @Test
    fun `saver round-trips index zero`() {
        val sync = GalleryStateSync()
        sync.setActiveIndex(postUri, 0)
        assertEquals(0, restore(save(sync)).peekActiveIndex(postUri))
    }

    private fun save(sync: GalleryStateSync): List<Any> =
        with(GalleryStateSyncSaver) {
            FakeSaverScope.save(sync).orEmpty()
        }

    private fun restore(saved: List<Any>): GalleryStateSync = requireNotNull(GalleryStateSyncSaver.restore(saved))

    /**
     * `Saver.save` runs inside a `SaverScope`, whose only job is to answer
     * whether a value can go into the bundle. Everything this saver emits is a
     * `String` or an `Int`, so the answer is always yes.
     */
    private object FakeSaverScope : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
