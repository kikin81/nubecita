package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ThreadPositionUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

/**
 * The bench timeline's numbered author thread (`benchthread1` … `benchthread5`
 * in `:core:feed-cache`'s `timeline.json`), as post detail reads it.
 *
 * It is shaped like the server's ordered thread read so the offline build can
 * exercise all of it: the opened post's earlier thread posts above it, the
 * later ones directly below, other people's replies after, and one "more
 * replies exist" row. To make that row reachable with only five posts, the fake
 * returns at most [POSTS_BELOW_FOCUS] thread posts below the opened one — the
 * same cut the server makes on a long thread, just sooner. Opening the post the
 * row names shows the rest.
 *
 * The post text is duplicated from the timeline fixture on purpose: the two
 * fixtures live in different modules' bench source sets, and a shared one would
 * cost a module dependency that exists for no production reason.
 */
private const val THREAD_AUTHOR_DID = "did:plc:carmen0000000000000000000"
private const val THREAD_LENGTH = 5
private const val POSTS_BELOW_FOCUS = 2

internal fun benchThreadPostUri(index: Int): String = "at://$THREAD_AUTHOR_DID/app.bsky.feed.post/benchthread$index"

private val threadBodies =
    listOf(
        "a thread on what a week of profiling on a five-year-old phone taught me. short version: the slow parts were never where I guessed.",
        "first surprise: startup. I was sure it was the database. it was a font being read from disk on the main thread, every single launch.",
        "second: scrolling. the list itself was fine. one card measured its text twice per frame, and that alone cost us the frame budget.",
        "third: images. decoding at full size and letting the view scale them down is free on a new phone and ruinous on an old one.",
        "so the habit I'm keeping: one slow device on the desk, used every day. it finds in ten seconds what a trace takes an hour to show.",
    )

private val threadAuthor =
    AuthorUi(
        did = THREAD_AUTHOR_DID,
        handle = "carmen.codes",
        displayName = "Carmen Ortiz",
        avatarUrl = "file:///android_asset/img/avatars/carmen.jpg",
    )

private val threadPosts: List<PostUi> =
    threadBodies.mapIndexed { i, body ->
        val index = i + 1
        PostUi(
            id = benchThreadPostUri(index),
            cid = "bafyreibenchthread$index",
            author = threadAuthor,
            createdAt = Instant.parse("2026-05-26T08:0$index:00Z"),
            text = body,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(replyCount = listOf(4, 1, 3, 1, 2)[i], repostCount = listOf(9, 2, 3, 1, 4)[i], likeCount = listOf(88, 31, 40, 27, 52)[i]),
            viewer = ViewerStateUi(isFollowingAuthor = true),
            repostedBy = null,
            threadRootUri = if (index > 1) benchThreadPostUri(1) else null,
            threadPosition = ThreadPositionUi(index = index, count = THREAD_LENGTH),
        )
    }

private fun threadReply(
    rkey: String,
    didSuffix: String,
    handle: String,
    displayName: String,
    avatar: String,
    body: String,
): PostUi =
    PostUi(
        id = "at://did:plc:$didSuffix/app.bsky.feed.post/$rkey",
        cid = "bafyreibench$rkey",
        author =
            AuthorUi(
                did = "did:plc:$didSuffix",
                handle = handle,
                displayName = displayName,
                avatarUrl = "file:///android_asset/img/avatars/$avatar",
            ),
        createdAt = Instant.parse("2026-05-26T08:30:00Z"),
        text = body,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(likeCount = 3),
        viewer = ViewerStateUi(),
        repostedBy = null,
        threadRootUri = benchThreadPostUri(1),
    )

private val visibleReplies: List<PostUi> =
    listOf(
        threadReply("benchthreadreply1", "benchdiego", "diego.outdoors", "Diego Ramos", "diego.jpg", "the font one gets everyone. we shipped that exact bug twice."),
        threadReply("benchthreadreply2", "benchgabe", "gabe.climbs", "Gabe Moreno", "gabe.jpg", "which phone? asking for a drawer full of old ones."),
    )

private val leftOutReplies: List<PostUi> =
    listOf(
        threadReply("benchthreadother1", "bob0000000000000000000000", "bob.dev", "Bob Iglesias", "bob.jpg", "counterpoint: a trace never lies about where the time went."),
        threadReply("benchthreadother2", "alice000000000000000000", "alice.bsky.social", "Alice Chen", "alice.jpg", "bookmarking this for the next perf week."),
    )

/**
 * The ordered thread opened at [focusUri], or null when [focusUri] is not one
 * of the author thread's posts.
 */
internal fun benchAuthorThread(focusUri: String): ImmutableList<ThreadItem>? {
    val focus = threadPosts.indexOfFirst { it.id == focusUri }
    if (focus < 0) return null
    return buildList {
        threadPosts.take(focus).forEach { add(ThreadItem.Ancestor(post = it)) }
        add(ThreadItem.Focus(post = threadPosts[focus]))
        val below = threadPosts.drop(focus + 1).take(POSTS_BELOW_FOCUS)
        below.forEachIndexed { i, post -> add(ThreadItem.Reply(post = post, depth = i + 1)) }
        val unshown = threadPosts.size - (focus + 1) - below.size
        if (unshown > 0) add(ThreadItem.MoreReplies(postUri = below.last().id, count = unshown, depth = below.size))
        visibleReplies.forEach { add(ThreadItem.Reply(post = it, depth = 1)) }
        add(ThreadItem.OtherReplies)
    }.toImmutableList()
}

/** What "show more replies" loads for the author thread; empty for any other post. */
internal fun benchAuthorThreadOtherReplies(focusUri: String): ImmutableList<ThreadItem> =
    if (threadPosts.none { it.id == focusUri }) {
        persistentListOf()
    } else {
        leftOutReplies.map { ThreadItem.Reply(post = it, depth = 1) }.toImmutableList()
    }
