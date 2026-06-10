package net.kikin.nubecita.feature.widgets.impl.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Pure file-store behavior for widget thumbnails (D-C5): key→file, evict, clearAccount. */
internal class WidgetThumbnailStoreTest {
    @TempDir
    lateinit var root: File

    private val store get() = WidgetThumbnailStore(root)

    @Test
    fun `thumbnailFile is deterministic per post and scoped to the account dir`() {
        val a = store.thumbnailFile(ACCOUNT, POST_A)
        val again = store.thumbnailFile(ACCOUNT, POST_A)
        val other = store.thumbnailFile(ACCOUNT, POST_B)

        assertEquals(a, again)
        assertNotEquals(a, other)
        // Parent account directory is created and the file sits under it.
        assertTrue(a.parentFile?.isDirectory == true)
        assertEquals(a.parentFile, other.parentFile)
    }

    @Test
    fun `write then read round-trips via hasThumbnail`() {
        assertFalse(store.hasThumbnail(ACCOUNT, POST_A))

        store.thumbnailFile(ACCOUNT, POST_A).writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(store.hasThumbnail(ACCOUNT, POST_A))
        assertFalse(store.hasThumbnail(ACCOUNT, POST_B))
    }

    @Test
    fun `evict deletes thumbnails for posts no longer in the head and keeps the rest`() {
        store.thumbnailFile(ACCOUNT, POST_A).writeBytes(byteArrayOf(1))
        store.thumbnailFile(ACCOUNT, POST_B).writeBytes(byteArrayOf(2))
        store.thumbnailFile(ACCOUNT, POST_C).writeBytes(byteArrayOf(3))

        // Head now holds only A and C → B is evicted.
        store.evict(ACCOUNT, keepPostIds = setOf(POST_A, POST_C))

        assertTrue(store.hasThumbnail(ACCOUNT, POST_A))
        assertFalse(store.hasThumbnail(ACCOUNT, POST_B))
        assertTrue(store.hasThumbnail(ACCOUNT, POST_C))
    }

    @Test
    fun `evict with an empty keep set clears the account's thumbnails`() {
        store.thumbnailFile(ACCOUNT, POST_A).writeBytes(byteArrayOf(1))

        store.evict(ACCOUNT, keepPostIds = emptySet())

        assertFalse(store.hasThumbnail(ACCOUNT, POST_A))
    }

    @Test
    fun `clearAccount drops only the target account's thumbnails`() {
        store.thumbnailFile(ACCOUNT, POST_A).writeBytes(byteArrayOf(1))
        store.thumbnailFile(OTHER_ACCOUNT, POST_A).writeBytes(byteArrayOf(9))

        store.clearAccount(ACCOUNT)

        assertFalse(store.hasThumbnail(ACCOUNT, POST_A))
        assertTrue(store.hasThumbnail(OTHER_ACCOUNT, POST_A))
    }

    private companion object {
        const val ACCOUNT = "did:plc:viewer"
        const val OTHER_ACCOUNT = "did:plc:other"
        const val POST_A = "at://did:plc:author/app.bsky.feed.post/aaa"
        const val POST_B = "at://did:plc:author/app.bsky.feed.post/bbb"
        const val POST_C = "at://did:plc:author/app.bsky.feed.post/ccc"
    }
}
