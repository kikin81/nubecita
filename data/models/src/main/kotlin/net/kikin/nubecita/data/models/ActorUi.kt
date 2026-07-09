package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * UI-ready projection of a Bluesky actor (user). Used by surfaces that
 * render a single actor row — composer's @-mention typeahead, search's
 * People tab, future profile lists, etc.
 *
 * `displayName` is nullable because the underlying lexicon allows
 * actors without a display name; consumers fall back to `handle` for
 * display. `avatarUrl` is nullable because actors without a profile
 * picture serve no avatar.
 *
 * Promoted from `:core:posting`'s former `ActorTypeaheadUi` in
 * `nubecita-vrba.4` — same shape, broader applicability, dropped the
 * misleading `Typeahead` suffix since it's now also used for non-
 * typeahead actor-row surfaces (search People tab, future profile lists).
 *
 * `canMessage` is a fail-open DM-eligibility hint (default true): false only
 * when the source response said the viewer can't DM this actor. See
 * `:core:profile` `canViewerMessage`.
 *
 * `verifiedBadge` is the account's verification tier, derived from the wire
 * `verificationState` via [toVerifiedBadge]; [VerifiedBadge.None] (the default)
 * renders no badge. It is populated on wire-mapped surfaces (search / typeahead
 * results map it directly from the response); actors read back from the DID
 * cache default to [VerifiedBadge.None] since the `actors` table doesn't persist
 * it — those surfaces (recency / DM pickers) don't render a badge today, so a
 * fresh search always supplies the live value where it's shown.
 */
@Immutable
public data class ActorUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val canMessage: Boolean = true,
    val verifiedBadge: VerifiedBadge = VerifiedBadge.None,
)
