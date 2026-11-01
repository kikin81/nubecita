package net.kikin.nubecita.designsystem.component

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction.BlockAuthor
import net.kikin.nubecita.designsystem.component.PostOverflowAction.CopyPostText
import net.kikin.nubecita.designsystem.component.PostOverflowAction.DeletePost
import net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor
import net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteThread
import net.kikin.nubecita.designsystem.component.PostOverflowAction.PinPost
import net.kikin.nubecita.designsystem.component.PostOverflowAction.ReportAuthor
import net.kikin.nubecita.designsystem.component.PostOverflowAction.ReportPost
import net.kikin.nubecita.designsystem.component.PostOverflowAction.TranslatePost
import net.kikin.nubecita.designsystem.component.PostOverflowAction.UnblockAuthor
import net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor
import net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteThread
import net.kikin.nubecita.designsystem.component.PostOverflowAction.UnpinPost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The overflow sheet's grouping and visibility rules (nubecita-iwjz.3),
 * shared by PostCard and the vertical video page.
 */
class PostOverflowRowsTest {
    @Test
    fun `own post groups are pin, utility, thread, delete`() {
        assertEquals(
            listOf(listOf(PinPost), listOf(TranslatePost, CopyPostText), listOf(MuteThread), listOf(DeletePost)),
            postOverflowGroups(post(ViewerStateUi(isOwnPost = true))).asLists(),
        )
    }

    @Test
    fun `others post groups end with moderation`() {
        assertEquals(
            listOf(
                listOf(TranslatePost, CopyPostText),
                listOf(MuteThread),
                listOf(MuteAuthor, BlockAuthor, ReportPost, ReportAuthor),
            ),
            postOverflowGroups(post(ViewerStateUi())).asLists(),
        )
    }

    @Test
    fun `blank text drops translate`() {
        val groups = postOverflowGroups(post(ViewerStateUi(), text = "  ")).asLists()
        assertEquals(listOf(CopyPostText), groups.first())
    }

    @Test
    fun `muted and blocked author show the undo of each pair`() {
        val groups =
            postOverflowGroups(
                post(ViewerStateUi(isAuthorMutedByViewer = true, isAuthorBlockedByViewer = true)),
            ).asLists()
        assertEquals(listOf(UnmuteAuthor, UnblockAuthor, ReportPost, ReportAuthor), groups.last())
    }

    @Test
    fun `thread muted shows unmute thread`() {
        val groups = postOverflowGroups(post(ViewerStateUi(isThreadMuted = true))).asLists()
        assertEquals(listOf(UnmuteThread), groups[1])
    }

    @Test
    fun `own post never offers moderation aimed at yourself`() {
        val all =
            postOverflowGroups(
                post(
                    ViewerStateUi(
                        isOwnPost = true,
                        isAuthorMutedByViewer = true,
                        isAuthorBlockedByViewer = true,
                    ),
                ),
            ).asLists().flatten()
        val selfAimed = setOf(ReportPost, ReportAuthor, MuteAuthor, UnmuteAuthor, BlockAuthor, UnblockAuthor)
        assertTrue(all.none { it in selfAimed }, "own post offered $all")
    }

    @Test
    fun `pinned own post offers unpin`() {
        val groups = postOverflowGroups(post(ViewerStateUi(isOwnPost = true, isPinned = true))).asLists()
        assertEquals(listOf(UnpinPost), groups.first())
    }

    @Test
    fun `own reply is pinnable`() {
        val groups =
            postOverflowGroups(
                post(ViewerStateUi(isOwnPost = true)).copy(threadRootUri = "at://did:plc:b/app.bsky.feed.post/root"),
            ).asLists()
        assertEquals(listOf(PinPost), groups.first())
    }

    @Test
    fun `others post has no pin row`() {
        val all = postOverflowGroups(post(ViewerStateUi(isPinned = true))).asLists().flatten()
        assertTrue(PinPost !in all && UnpinPost !in all, "others' post offered $all")
    }

    private fun List<PostOverflowGroup>.asLists(): List<List<PostOverflowAction>> = map { it.actions.toList() }

    private fun post(
        viewer: ViewerStateUi,
        text: String = "hello",
    ): PostUi =
        PostUi(
            id = "at://did:plc:a/app.bsky.feed.post/1",
            cid = "bafy",
            author = AuthorUi(did = "did:plc:a", handle = "a.bsky.social", displayName = "A", avatarUrl = null),
            createdAt = Instant.parse("2026-01-01T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = viewer,
            repostedBy = null,
        )
}
