package net.kikin.nubecita.feature.composer.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the unified post composer.
 *
 * One route handles both modes:
 * - **New post** when [replyToUri] is `null`.
 * - **Reply to an existing post** when [replyToUri] is non-null and
 *   carries the AT URI of the parent (e.g.
 *   `at://did:plc:.../app.bsky.feed.post/3kxyz`).
 *
 * No second `NavKey` exists for either mode — per the unified-composer
 * spec, the composer is one screen / one ViewModel / one set of fixtures.
 *
 * Stored as a `String?` — not the lexicon-typed `AtUri` value class —
 * so the NavKey serialization format stays a single primitive nullable
 * field; consumers wrap to `AtUri` at the call site to the atproto
 * runtime, mirroring the pattern from [PostDetailRoute] and
 * `:feature:feed:impl`'s like/repost path. This keeps
 * `:feature:composer:api` atproto-runtime-free — the api module
 * depends only on `androidx.navigation3.runtime` and
 * `kotlinx.serialization.json`, never on the atproto SDK.
 *
 * Lives in `:feature:composer:api` so cross-feature surfaces
 * (`:feature:feed:impl`'s reply affordance, the Feed-tab compose FAB)
 * can depend on this module alone — never on `:feature:composer:impl`.
 * `:app` and `MainShell` push instances of `ComposerRoute` onto the
 * inner `NavDisplay` (Compact width) or feed them to the
 * `MainShell`-scoped composer launcher (Medium/Expanded width) per the
 * adaptive-container requirement.
 */
@Serializable
data class ComposerRoute(
    val replyToUri: String? = null,
) : NavKey
