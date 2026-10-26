package net.kikin.nubecita.core.wearsync

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import kotlin.time.Instant

internal fun testPost(
    id: String,
    authorDid: String = "did:plc:alice",
    avatarUrl: String? = "https://cdn/avatar/alice.jpg",
    embed: EmbedUi = EmbedUi.Empty,
    viewer: ViewerStateUi = ViewerStateUi(),
): PostUi =
    PostUi(
        id = id,
        cid = "cid-$id",
        author = AuthorUi(did = authorDid, handle = "$authorDid.handle", displayName = "Name $authorDid", avatarUrl = avatarUrl),
        createdAt = Instant.fromEpochMilliseconds(1_000),
        text = "text $id",
        facets = persistentListOf(),
        embed = embed,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 3),
        viewer = viewer,
        repostedBy = null,
    )
