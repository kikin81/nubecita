package net.kikin.nubecita.data.models

/**
 * Test + preview fixtures for [PinnedFeedUi].
 *
 * Lives in `src/test` so production code never accidentally instantiates
 * fake data. One helper per [FeedKind], mirroring [PostUiFixtures].
 */
internal object PinnedFeedUiFixtures {
    fun fakeFollowing(
        id: String = "following",
        uri: String = "following",
        displayName: String = "Following",
    ): PinnedFeedUi =
        PinnedFeedUi(
            id = id,
            uri = uri,
            kind = FeedKind.Following,
            displayName = displayName,
            // Following entries render the local Home glyph — no remote avatar.
            avatarUrl = null,
        )

    fun fakeGenerator(
        id: String = "whats-hot",
        uri: String = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot",
        displayName: String = "Discover",
        avatarUrl: String? = "https://cdn.example/avatars/whats-hot.jpg",
    ): PinnedFeedUi =
        PinnedFeedUi(
            id = id,
            uri = uri,
            kind = FeedKind.Generator,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )

    fun fakeList(
        id: String = "list-1",
        uri: String = "at://did:plc:fakedid000000000000000/app.bsky.graph.list/abc123",
        displayName: String = "My List",
        avatarUrl: String? = "https://cdn.example/avatars/my-list.jpg",
    ): PinnedFeedUi =
        PinnedFeedUi(
            id = id,
            uri = uri,
            kind = FeedKind.List,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )
}
