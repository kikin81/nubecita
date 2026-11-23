package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Fixture factories for [StarterPackUi], for previews, screenshot tests and unit
 * tests in the modules that draw packs. Lives in `src/main` so downstream
 * `screenshotTest` and `test` source sets can reach it. Every parameter has a
 * default so callers vary only what they assert on.
 */
public object StarterPackUiFixtures {
    public fun pack(
        slug: String = "androiddev",
        name: String = "Android developers",
        creatorHandle: String = "jglypt.net",
        memberCount: Int? = 42,
        avatarUrls: ImmutableList<String> = persistentListOf(),
    ): StarterPackUi =
        StarterPackUi(
            uri = "at://did:plc:$slug/app.bsky.graph.starterpack/$slug",
            name = name,
            creatorHandle = creatorHandle,
            memberCount = memberCount,
            avatarUrls = avatarUrls,
        )

    /** A pack whose response carried [count] member avatars (placeholder URLs, never loaded in tests). */
    public fun packWithAvatars(
        count: Int = 4,
        slug: String = "androiddev",
        name: String = "Android developers",
        memberCount: Int? = 42,
    ): StarterPackUi =
        pack(
            slug = slug,
            name = name,
            memberCount = memberCount,
            avatarUrls = List(count) { "https://example.test/avatar/$slug/$it.jpg" }.toImmutableList(),
        )

    public fun packs(count: Int = 3): ImmutableList<StarterPackUi> =
        List(count) { index ->
            pack(
                slug = "pack$index",
                name = "Starter pack ${index + 1}",
                creatorHandle = "creator$index.bsky.social",
                memberCount = 10 + index,
            )
        }.toImmutableList()
}
