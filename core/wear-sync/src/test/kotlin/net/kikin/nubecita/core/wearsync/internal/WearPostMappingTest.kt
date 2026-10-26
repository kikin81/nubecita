package net.kikin.nubecita.core.wearsync.internal

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.wearsync.testPost
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearPostMappingTest {
    private val image = ImageUi(fullsizeUrl = "https://cdn/full.jpg", thumbUrl = "https://cdn/thumb.jpg", altText = null, aspectRatio = 1f)
    private val warning = MediaContentWarning(ContentWarningCategory.GRAPHIC_MEDIA, overridable = true)

    @Test
    fun maps_text_counts_and_viewer_state() {
        val post = testPost("p1", viewer = ViewerStateUi(isLikedByViewer = true, likeUri = "at://like/1", isBookmarked = true))

        val wear = listOf(post).toWearFeedDraft().posts.single()

        assertEquals("p1", wear.uri)
        assertEquals("cid-p1", wear.cid)
        assertEquals("text p1", wear.text)
        assertEquals(3, wear.likeCount)
        assertEquals("at://like/1", wear.viewer.likeUri)
        assertTrue(wear.viewer.bookmarked)
    }

    @Test
    fun one_avatar_asset_per_distinct_url() {
        val draft = listOf(testPost("p1"), testPost("p2"), testPost("p3", authorDid = "did:plc:bob", avatarUrl = "https://cdn/avatar/bob.jpg")).toWearFeedDraft()

        assertEquals(2, draft.avatarUrls.size)
        assertEquals(draft.posts[0].author.avatarAssetKey, draft.posts[1].author.avatarAssetKey)
    }

    @Test
    fun image_post_gets_its_first_thumbnail_and_count() {
        val draft = listOf(testPost("p1", embed = EmbedUi.Images(persistentListOf(image, image)))).toWearFeedDraft()

        val media = draft.posts.single().media
        assertEquals(2, media?.imageCount)
        assertEquals("https://cdn/thumb.jpg", draft.thumbnailUrls[media?.thumbnailAssetKey])
    }

    @Test
    fun media_behind_a_content_warning_gets_no_thumbnail() {
        val draft = listOf(testPost("p1", embed = EmbedUi.Images(persistentListOf(image), contentWarning = warning))).toWearFeedDraft()

        val media = draft.posts.single().media
        assertTrue(media?.hasContentWarning == true)
        assertNull(media?.thumbnailAssetKey)
        assertTrue(draft.thumbnailUrls.isEmpty())
    }

    @Test
    fun video_uses_its_poster() {
        val video = EmbedUi.Video(posterUrl = "https://cdn/poster.jpg", playlistUrl = "https://cdn/v.m3u8", aspectRatio = 1f, durationSeconds = 10, altText = null)

        val draft = listOf(testPost("p1", embed = video)).toWearFeedDraft()

        assertTrue(
            draft.posts
                .single()
                .media
                ?.isVideo == true,
        )
        assertEquals(listOf("https://cdn/poster.jpg"), draft.thumbnailUrls.values.toList())
    }

    @Test
    fun muted_and_blocked_authors_are_dropped() {
        val posts =
            listOf(
                testPost("muted", viewer = ViewerStateUi(isAuthorMutedByViewer = true)),
                testPost("blocked", viewer = ViewerStateUi(isAuthorBlockedByViewer = true)),
                testPost("blocking", viewer = ViewerStateUi(isAuthorBlockingViewer = true)),
                testPost("ok"),
            )

        assertEquals(listOf("ok"), posts.toWearFeedDraft().posts.map { it.uri })
    }
}
