package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion

/**
 * Carries a preference read from `app.bsky.actor.getPreferences` back into a
 * `putPreferences` request, for the read-modify-write in [DefaultPinnedFeedsRepository].
 *
 * Concrete pref types (`SavedFeedsPrefV2`, `AdultContentPref`, etc.) implement BOTH
 * union interfaces, so the `else` branch casts them straight across. The GET-side
 * [GetPreferencesResponsePreferencesUnion.Unknown] is remapped to the PUT-side
 * [PutPreferencesRequestPreferencesUnion.Unknown] so unmodelled future preference
 * kinds survive the round-trip intact.
 *
 * This is a module-local copy of the same extension in `:core:moderation` —
 * duplicated rather than shared because both modules declare `internal` visibility
 * and there is no shared `:core:preferences` home yet.
 */
internal fun GetPreferencesResponsePreferencesUnion.asPutPreference(): PutPreferencesRequestPreferencesUnion =
    when (this) {
        is GetPreferencesResponsePreferencesUnion.Unknown ->
            PutPreferencesRequestPreferencesUnion.Unknown(type, raw)
        else -> this as PutPreferencesRequestPreferencesUnion
    }
