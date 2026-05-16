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
 */
@Immutable
public data class ActorUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
