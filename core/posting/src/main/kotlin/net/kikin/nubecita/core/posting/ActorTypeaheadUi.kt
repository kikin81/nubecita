package net.kikin.nubecita.core.posting

/**
 * UI-shaped projection of one actor returned by
 * [ActorTypeaheadRepository.searchTypeahead]. Holds only the fields the
 * composer's suggestion row renders, and only as primitive [String]
 * types — feature-side consumers don't transitively depend on the
 * atproto SDK's [io.github.kikin81.atproto.runtime.Did] /
 * [io.github.kikin81.atproto.runtime.Handle] /
 * [io.github.kikin81.atproto.runtime.Uri] value classes through this
 * boundary.
 *
 * Field semantics:
 *  - [did] is the actor's stable identifier (`did:plc:…` / `did:web:…`).
 *    The composer uses this as the `LazyColumn` row key, so two
 *    consecutive responses re-rendering the same actor reuse the row.
 *  - [handle] is the canonical handle string (no leading `@`). When the
 *    user taps a row, the composer inserts `"@$handle "` at the active
 *    `@`-token position.
 *  - [displayName] is the actor's free-form display name, normalized
 *    to `null` when the upstream `ProfileViewBasic.displayName` is
 *    `null`-or-blank. Suggestion rows fall back to rendering the
 *    handle as the primary line when this is null.
 *  - [avatarUrl] is the avatar CDN URL, or `null` when the upstream
 *    `ProfileViewBasic.avatar` is missing. The composer's row uses
 *    `NubecitaAsyncImage` which renders a default placeholder when
 *    null is passed.
 *
 * Not annotated `@Stable` deliberately: this module is not a Compose
 * module, and a `data class` of `String` / `String?` is already
 * implicitly stable for Compose consumers without dragging the
 * `androidx.compose.runtime` dependency into `:core:posting`.
 */
data class ActorTypeaheadUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
