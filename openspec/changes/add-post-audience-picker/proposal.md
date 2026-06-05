## Why

Bluesky authors can restrict **who replies** (`app.bsky.feed.threadgate`) and **who quotes** (`app.bsky.feed.postgate`) their posts. Nubecita already *reads* these gates (the `canViewerReply` work shipped in `lq9t.3.1`), but offers no way to *set* them when composing — so every post Nubecita publishes is wide-open. This change adds a composer affordance to choose the audience before posting, plus a synced default, matching the control the official client offers.

## What Changes

- A composer **audience chip** ("Visible to all" / "Interaction limited") in the options row of a **new top-level** post, hidden on replies.
- An adaptive **`AudiencePicker`** (bottom sheet on phone / centered popup on tablet, mirroring the existing `LanguagePicker`) to pick **who can reply** (Anyone / Nobody / a combination of followers, following, mentioned) and whether to **allow quote posts**, with a "save as default" checkbox and a "reset to default" affordance.
- On post submit, write `threadgate` + `postgate` records sharing the new post's rkey — **best-effort**, so a gate-write failure never fails the already-live post.
- A **saved default** persisted to the synced atproto preference `app.bsky.actor.defs#postInteractionSettingsPref` (no Room / no local DataStore), via a repository mirroring `ModerationPreferencesRepository`.
- A new `NubecitaIconName.Globe` glyph, added via the safe font-subset regeneration procedure.

## Capabilities

### New Capabilities
- `post-audience`: choosing reply/quote audience for a new post, writing the threadgate/postgate records, and persisting a synced per-account default.

### Modified Capabilities
<!-- None: composer/posting behavior is extended additively; no existing spec-level requirement changes. -->

## Impact

- **`:core:posting`** — new `PostAudience` / `ReplyAudience` model; `PostingRepository.createPost` gains an `audience` param and writes threadgate/postgate after the post.
- **`:core:moderation` (or a sibling repo)** — new synced-default repository over `postInteractionSettingsPref`, reusing the raw-JSON read-modify-write array pattern.
- **`:feature:composer:impl`** — `AudiencePicker` + `AudiencePickerContent`, the chip, and `ComposerState`/`ComposerEvent`/VM wiring; pre-fill from the saved default.
- **`:designsystem`** — `NubecitaIconName.Globe` + regenerated Material Symbols subset (`.ttf`); one screenshot baseline (`NubecitaIconShowcase`) regenerated on CI via the `update-baselines` label.
- **Dependencies:** atproto-kotlin 9.1.0 (all record/rule types confirmed present).
- **Deferred (tracked separately):** "select from your lists" (threadgate `listRule`); postgate-on-replies; editing an already-posted thread's gate.
