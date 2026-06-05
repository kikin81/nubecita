## Context

Nubecita can read reply/quote gates but cannot set them. This change adds the write path and a composer affordance to choose the audience before posting. The relevant AT Protocol surface:

- **`app.bsky.feed.threadgate`** — controls who can reply. Lives at the thread *root*; its rkey MUST match the post's rkey.
- **`app.bsky.feed.postgate`** — controls who can quote. Same rkey-matches-post rule.
- **`app.bsky.actor.defs#postInteractionSettingsPref`** — a synced preference holding the author's default gate, stored in the same `app.bsky.actor.getPreferences` array the moderation prefs already use.

All record/rule types were verified present in **atproto-kotlin 9.1.0**; the preferences array is mis-modeled by the SDK (the `preferences` field is typed as `JsonObject`, not an array), so the synced-default path must use the raw-JSON read-modify-write already proven in `ModerationPreferencesRepository`.

## Goals / Non-Goals

**Goals:**
- Let the author choose reply/quote audience for a **new top-level** post and post with those gates applied.
- Persist a per-account default to the synced atproto preference.
- Reuse existing patterns (LanguagePicker presentation, moderation-repo raw-JSON array, optimistic+revert) rather than introducing new mechanisms.

**Non-Goals:**
- "Select from your lists" (threadgate `listRule`) — lists aren't supported yet. Deferred ticket.
- Postgate on replies — threadgate belongs to the thread root, so the chip is hidden on replies; quote-control on replies is a later refinement.
- Editing an already-posted thread's gate (V2).
- Room / local persistence of the default — the synced preference is the single source of truth.

## Decisions

### D1 — Presentation mirrors `LanguagePicker`, not a nav route
`AudiencePicker` is an inline composable opened from local `rememberSaveable { showAudiencePicker }` state — `ModalBottomSheet` on Compact, `Popup` + centered `Surface` (~480dp) on Medium/Expanded. Deliberately **not** a Compose `Dialog` (avoids the double-scrim when the composer is itself a tablet dialog) and **not** a Nav3 route (avoids the "tap audience → post dismissed → draft lost" failure mode the user explicitly called out). The picker holds its own `draft: PostAudience` until confirm; cancel discards.

### D2 — Model in `:core:posting`
```kotlin
data class PostAudience(val reply: ReplyAudience, val allowQuotes: Boolean) {
    companion object { val DEFAULT = PostAudience(ReplyAudience.Everyone, allowQuotes = true) }
}
sealed interface ReplyAudience {
    data object Everyone : ReplyAudience            // → NO threadgate record
    data object Nobody : ReplyAudience              // → threadgate, allow = [] (empty)
    data class Combination(val followers: Boolean, val following: Boolean, val mentioned: Boolean) : ReplyAudience
}
```
`@Stable`/`@Immutable` so it lives on `ComposerState` alongside `ComposerAttachment`.

### D3 — Record mapping (named args; field orders are alphabetical)
| UI | Record |
|---|---|
| `reply = Everyone` | **no threadgate record** |
| `reply = Nobody` | `Threadgate(post, createdAt, allow = Defined(emptyList()))` |
| `reply = Combination(f,fo,m)` | `Threadgate(..., allow = Defined([followerRule?, followingRule?, mentionRule?]))` for checked boxes |
| `allowQuotes = true` | **no postgate record** |
| `allowQuotes = false` | `Postgate(post, createdAt, embeddingRules = Defined([PostgateDisableRule()]))` |

- `Threadgate(allow: AtField<List<ThreadgateAllowUnion>> = Missing, createdAt: Datetime, hiddenReplies = Missing, post: AtUri)` — `createdAt` is a plain `Datetime`, not `AtField`.
- Rules (no-arg): `ThreadgateMentionRule()`, `ThreadgateFollowingRule()`, `ThreadgateFollowerRule()`; `ThreadgateListRule(list: AtUri)` deferred.
- `Postgate(createdAt: Datetime, detachedEmbeddingUris = Missing, embeddingRules = Missing, post: AtUri)`; disable member `PostgateDisableRule()`.
- **Critical semantics:** `allow = Missing/omitted` = *anyone*; `allow = Defined(emptyList())` = *nobody*. Never send `Defined(emptyList())` when you mean "anyone."

### D4 — Posting write is best-effort
Extend `PostingRepository.createPost(..., audience: PostAudience = PostAudience.DEFAULT)`. After the post's `createRecord` returns, extract `val rkey = RecordKey(response.uri.raw.substringAfterLast('/'))` and write the threadgate (if `reply != Everyone`) and postgate (if `!allowQuotes`) via `CreateRecordRequest(..., rkey = Defined(rkey), ...)`. A gate-write failure MUST NOT fail the post: log the error identity (matching the repo's redaction discipline), still return the post URI, and surface a non-blocking "Couldn't apply audience settings" via a typed result the composer maps to a snackbar.

### D5 — Saved default = synced preference, no Room
A new repository mirrors `ModerationPreferencesRepository` exactly: raw-JSON `fetchPreferencesArray()`/`writePreferencesArray()`, a seeded `MutableStateFlow<PostAudience>(DEFAULT)`, `refresh()`, `resetToDefault()` on sign-out, `writeMutex`, and the **optimistic + revert** `update {}` pattern (same one landing in `twmt.6`). It owns only the `postInteractionSettingsPref` entry (`threadgateAllowRules` / `postgateEmbeddingRules`, both omitted = DEFAULT) and preserves every foreign entry. Placement (extend `:core:moderation` vs a sibling repo) is an implementation call kept in one place. The composer pre-fills `ComposerState.audience` from this default at construction.

### D6 — Globe icon via safe font subset
Add `NubecitaIconName.Globe("")` (alphabetical). Regenerate the Material Symbols subset the safe way (venv fonttools, verify `globe == U+E64C`, diff `getCoordinates` for shared codepoints requiring `drifted outlines: 0`), commit enum + `.ttf` together, and regenerate the single changed baseline (`NubecitaIconShowcase`) on CI via the `update-baselines` PR label — never hand-commit local Mac baselines.

## Risks / Trade-offs

- **Gate write races the post being seen.** Best-effort means a brief window where the post is live but ungated. Acceptable: the alternative (failing/rolling back a live post) is worse. Snackbar tells the author.
- **Preference array mis-modeling.** Mitigated by reusing the moderation repo's proven raw-JSON path verbatim rather than the typed SDK pref.
- **Icon-font regeneration blast radius.** Mitigated by the `drifted outlines: 0` gate and CI baseline regen; only `NubecitaIconShowcase` should change.
- **Two write surfaces for the same rule `$type`s** (record vs preference). Kept consistent by sharing the UI→rule mapping helper between the posting write and the default repo.
