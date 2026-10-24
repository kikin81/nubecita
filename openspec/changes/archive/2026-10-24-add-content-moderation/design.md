## Context

The AT Protocol attaches moderation **labels** to content (`PostView.labels: com.atproto.label.defs#label[]`), and stores the viewer's content-filter choices in their account preferences. The official Bluesky app resolves (labels × prefs × subscribed labelers) into a per-target moderation decision. atproto-kotlin already exposes the wire types (`Label`, `AdultContentPref`, `ContentLabelPref`, `LabelersPref`) and `ActorService.getPreferences`/`putPreferences`. Our feed-mapping currently drops `PostView.labels`. This change builds only the `:core:moderation` foundation (`twmt.1`); it is consumed by later children.

## Goals / Non-Goals

**Goals:**
- A pure, fully-unit-tested decision function: `(labels, authorDid, prefs) → MediaModerationDecision`.
- A repository that fetches the user's prefs, caches them as a `StateFlow`, and write-throughs changes (whole-array read-modify-write).
- Encode Bluesky's defaults and the adult master-gate semantics exactly.

**Non-Goals (this child):**
- No UI, no feed-mapping/model changes, no render surfaces (those are `twmt.2`–`.4`).
- No custom-labeler subscription management, no account-level label hiding, no muted words (out of v1 scope per the epic).

## Decisions

**D1 — Domain model.** `ContentLabel` enum for the 4 v1 categories, each with its atproto label value and an `isAdult` flag: `PORN("porn", adult)`, `SEXUAL("sexual", adult)`, `GRAPHIC_MEDIA("graphic-media", adult)`, `NUDITY("nudity", not-adult)`. `LabelVisibility { SHOW, WARN, HIDE }` (the wire `ignore`/`show` both map to `SHOW`). `ModerationPrefs(adultContentEnabled: Boolean, visibilities: Map<ContentLabel, LabelVisibility>)` with a `DEFAULT` seed (`adult=false`; porn=HIDE, sexual=WARN, graphic-media=WARN, nudity=SHOW). `MediaModerationDecision`: `Show | Warn(category, overridable) | FilterFromFeed(category)`.

**D2 — The resolver (the load-bearing, Play-critical rule).** For each label on a post: resolve the effective visibility — **if the label is adult and `adultContentEnabled` is false → forced `HIDE` with `noOverride = true`** (no "show anyway"), regardless of the per-category setting; otherwise use the per-category visibility (falling back to the Bluesky default). The strongest decision across a post's labels wins: any `HIDE` → `FilterFromFeed`; else any `WARN` → `Warn`; else `Show`. Self-labels (`label.src == authorDid`) and labels from the default Bluesky labeler are honored; the author's own content is never filtered/blurred. *Alternative considered:* evaluating in the UI — rejected (must be precomputed off the render path for 120hz, and centralizing the rule keeps the Play-critical logic in one tested place).

**D3 — Context-dependent outcome.** `FilterFromFeed` means "drop from feed/search/profile lists"; the same labeled post opened **directly in post-detail** instead **covers the media** (no reveal when `noOverride`). The decision type carries enough (category + overridable) for the render layer (`twmt.4`) to apply the right behavior per context.

**D4 — Preferences I/O.** `getPreferences` returns `{ "preferences": [ {$type…}, … ] }`; the SDK mis-types the array as an object, so decode the raw body as `JsonObject` and read the array ourselves (exactly as `:core:feeds/FeedsDataSource` does). Extract `adultContentPref.enabled` + the global `contentLabelPref` entries (no `labelerDid`) into `ModerationPrefs`. **Save = read-modify-write:** fetch the full array, replace only our `adultContentPref` / `contentLabelPref` entries (preserving every other preference kind untouched), and `putPreferences` the whole array back (built as a raw `JsonObject` with a `preferences` array, sent via `XrpcClient.procedure`). Expose a cached `StateFlow<ModerationPrefs>` so feed filtering needs no per-load network call; `refresh()` repopulates it.

## Risks / Trade-offs

- **putPreferences whole-array clobber.** Writing the array back risks dropping preference kinds we don't model. → Mitigation: read-modify-write preserves unknown entries verbatim (we only swap our own `$type`s); covered by a parse/merge unit test.
- **Stale cache vs server.** A cached `StateFlow` can lag the server. → Acceptable: `refresh()` on app foreground / settings open; writes update the cache optimistically + persist.
- **Adult-gate correctness is Play-critical.** A bug that shows adult media when the gate is off is a policy violation. → Mitigation: the resolver is pure and exhaustively unit-tested (adult on/off × each category × self/labeler), and the default is `adult=false`.

## Migration Plan

Additive: a new module + bindings. Nothing consumes it until `twmt.2`–`.4`, so it ships dormant. Rollback = revert.

## Open Questions

- None blocking. The exact `putPreferences` `XrpcClient.procedure` overload is verified against the SDK during implementation (the read path already has precedent in `:core:feeds`).
