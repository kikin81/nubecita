package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion

/**
 * Carry a preference read from `app.bsky.actor.getPreferences` back into a
 * `putPreferences` request, for the read-modify-write that both preference
 * repositories in this module perform.
 *
 * Concrete pref types (`adultContentPref`, `contentLabelPref`, `savedFeedsPrefV2`,
 * `postInteractionSettingsPref`, …) implement BOTH the GET and PUT union
 * interfaces, so the `else` branch casts them straight across. That cast rests
 * on the atproto-kotlin codegen contract (every concrete pref in both endpoints
 * implements both unions); the repositories' `merge then parse round-trips`
 * tests guard it — a pref that couldn't be cast would fail them loudly. Only the
 * GET-side [GetPreferencesResponsePreferencesUnion.Unknown] — an unmodeled /
 * future preference kind — is remapped to the PUT-side
 * [PutPreferencesRequestPreferencesUnion.Unknown], preserving its `type` + raw
 * body so foreign entries survive the round-trip byte-for-byte.
 *
 * **Decode-strictness trade-off.** Reading via the typed `getPreferences()`
 * deserializes the WHOLE array up front, so — unlike the previous lenient
 * raw-JSON scan — a field-level decode failure in ANY known pref entry would
 * throw and take down all three preference readers (feeds, moderation,
 * post-audience) at once. This is bounded and accepted: the SDK's default Json
 * sets `ignoreUnknownKeys = true`, so *additive* lexicon evolution (the common
 * case) is tolerated; only a new *required* field or a type change on a known
 * pref would fail, and we control the SDK + lexicon version. Unknown `$type`s
 * are always safe via the `Unknown` member above.
 */
internal fun GetPreferencesResponsePreferencesUnion.asPutPreference(): PutPreferencesRequestPreferencesUnion =
    when (this) {
        is GetPreferencesResponsePreferencesUnion.Unknown -> PutPreferencesRequestPreferencesUnion.Unknown(type, raw)
        else -> this as PutPreferencesRequestPreferencesUnion
    }
