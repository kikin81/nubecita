package net.kikin.nubecita.feature.videoplayer.impl.data

/**
 * Resolves an AT-URI post → its video embed's playlist + poster URLs.
 *
 * Wraps `app.bsky.feed.getPosts(uris = [postUri])` and extracts the
 * post's `EmbedUi.Video`. Returns failure if the post has no video
 * embed (caller-error), if the fetch fails, or if the response is
 * malformed.
 */
internal interface VideoPostResolver {
    suspend fun resolve(postUri: String): Result<ResolvedVideoPost>
}

internal data class ResolvedVideoPost(
    val playlistUrl: String,
    val posterUrl: String?,
    val durationSeconds: Int?,
    val altText: String?,
)
