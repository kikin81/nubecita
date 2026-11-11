package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

/**
 * Threads for the bench timeline's foreign-language posts, keyed by post uri.
 *
 * Exists so translation can be exercised across both surfaces offline: tap a
 * foreign post in the feed and its thread opens with replies in a language the
 * reader can put on the auto list (Japanese), one they haven't (German), and
 * their own (English). The focus mirrors the feed fixture's post exactly, id
 * included, so a translation made in the feed is the same post here.
 *
 * Every other uri still gets the gallery thread, so the marketing capture and
 * the handle-vs-DID deep-link repro are untouched.
 */
internal val benchForeignThreads: Map<String, ImmutableList<ThreadItem>> =
    mapOf(
        TOKYO_POST_URI to
            listOf<ThreadItem>(
                ThreadItem.Focus(
                    post =
                        foreignPost(
                            id = TOKYO_POST_URI,
                            didSuffix = "lang00000000000000000002",
                            handle = "tokyo.bsky.social",
                            displayName = "ハルカ",
                            avatar = "carmen.jpg",
                            createdAt = "2026-05-26T12:00:00Z",
                            text = "今日の東京はとても暑いです。駅までの道のりで汗だくになりました。みなさんも水分補給を忘れずに。",
                            langs = listOf("ja"),
                        ),
                ),
                reply(
                    rkey = "3ktokyo0001",
                    didSuffix = "lang00000000000000000010",
                    handle = "kaito.bsky.social",
                    displayName = "カイト",
                    avatar = "diego.jpg",
                    text = "大阪も同じです。今日は一日中エアコンの前から動けませんでした。",
                    langs = listOf("ja"),
                ),
                reply(
                    rkey = "3ktokyo0002",
                    didSuffix = "lang00000000000000000011",
                    handle = "lena.bsky.social",
                    displayName = "Lena",
                    avatar = "bob.jpg",
                    text = "In Berlin regnet es seit drei Tagen. Ich würde gerne tauschen!",
                    langs = listOf("de"),
                ),
                reply(
                    rkey = "3ktokyo0003",
                    didSuffix = "lang00000000000000000012",
                    handle = "sam.bsky.social",
                    displayName = "Sam",
                    avatar = "alice.jpg",
                    text = "Stay cool out there! It's been a mild week here.",
                    langs = listOf("en"),
                ),
                reply(
                    rkey = "3ktokyo0004",
                    didSuffix = "lang00000000000000000013",
                    handle = "yui.bsky.social",
                    displayName = "ユイ",
                    avatar = "carmen.jpg",
                    text = "麦茶を冷やしておくのがおすすめです。\n塩分も少し取ってくださいね。",
                    langs = listOf("ja"),
                ),
            ).toImmutableList(),
    )

/** The feed fixture's ハルカ post, which opens [benchForeignThreads]' thread. */
internal const val TOKYO_POST_URI = "at://did:plc:lang00000000000000000002/app.bsky.feed.post/3klang0002"

private fun reply(
    rkey: String,
    didSuffix: String,
    handle: String,
    displayName: String,
    avatar: String,
    text: String,
    langs: List<String>,
): ThreadItem =
    ThreadItem.Reply(
        post =
            foreignPost(
                id = "at://did:plc:$didSuffix/app.bsky.feed.post/$rkey",
                didSuffix = didSuffix,
                handle = handle,
                displayName = displayName,
                avatar = avatar,
                createdAt = "2026-05-26T13:00:00Z",
                text = text,
                langs = langs,
            ),
        depth = 1,
    )

private fun foreignPost(
    id: String,
    didSuffix: String,
    handle: String,
    displayName: String,
    avatar: String,
    createdAt: String,
    text: String,
    langs: List<String>,
): PostUi =
    PostUi(
        id = id,
        cid = "bafyrei${didSuffix.takeLast(DID_TAIL)}00000000000000000000000000000000000000000",
        author =
            AuthorUi(
                did = "did:plc:$didSuffix",
                handle = handle,
                displayName = displayName,
                avatarUrl = "file:///android_asset/img/avatars/$avatar",
            ),
        createdAt = Instant.parse(createdAt),
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 0, repostCount = 1, likeCount = 4),
        viewer = ViewerStateUi(),
        repostedBy = null,
        langs = langs.toImmutableList(),
    )

private const val DID_TAIL = 8
